package com.learningcompanion.auth.application;

import com.learningcompanion.auth.domain.PasswordResetToken;
import com.learningcompanion.auth.domain.PasswordResetTokenRepository;
import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.security.AuthenticatedUser;
import com.learningcompanion.shared.security.PasswordHasher;
import com.learningcompanion.shared.security.RateLimiter;
import com.learningcompanion.shared.security.SessionTokenService;
import com.learningcompanion.shared.web.ClientInfo;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quên mật khẩu, đặt lại và đổi mật khẩu (NCL-01-CN-003, QTN-02).
 * Yêu cầu đặt lại luôn trả cùng một kết quả để không lộ địa chỉ nào đã đăng ký.
 * Đổi hoặc đặt lại mật khẩu xong thì các phiên cũ bị thu hồi và chủ tài khoản nhận thư xác nhận.
 */
@Service
public class PasswordService {

    private static final Duration HOUR = Duration.ofHours(1);
    private static final String CHANGE_CONTEXT = "CHANGE_PASSWORD";

    private final UserAccountRepository accounts;
    private final PasswordResetTokenRepository resetTokens;
    private final SessionTokenService tokens;
    private final PasswordPolicyValidator passwordPolicy;
    private final PasswordHasher passwordHasher;
    private final SessionService sessionService;
    private final LoginAttemptService loginAttempts;
    private final RateLimiter rateLimiter;
    private final AuditLogger audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final AppProperties.Auth settings;

    public PasswordService(UserAccountRepository accounts, PasswordResetTokenRepository resetTokens,
                           SessionTokenService tokens, PasswordPolicyValidator passwordPolicy,
                           PasswordHasher passwordHasher, SessionService sessionService,
                           LoginAttemptService loginAttempts, RateLimiter rateLimiter, AuditLogger audit,
                           ApplicationEventPublisher events, Clock clock, AppProperties properties) {
        this.accounts = accounts;
        this.resetTokens = resetTokens;
        this.tokens = tokens;
        this.passwordPolicy = passwordPolicy;
        this.passwordHasher = passwordHasher;
        this.sessionService = sessionService;
        this.loginAttempts = loginAttempts;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
        this.settings = properties.auth();
    }

    /**
     * Gửi thư đặt lại nếu địa chỉ có tài khoản đăng nhập được (kể cả đang chờ xóa, để người quên mật khẩu vẫn vào
     * hủy được yêu cầu xóa); phản hồi như nhau trong mọi trường hợp.
     */
    @Transactional
    public void requestReset(String email, ClientInfo client) {
        throttle(client);
        accounts.findByEmail(EmailNormalizer.normalize(email))
                .filter(UserAccount::canSignIn)
                .ifPresent(account -> issueIfCooledDown(account, client, "BY_EMAIL"));
    }

    /** Gửi thư mới từ trang báo đường dẫn hết hạn, không bắt người học nhập lại địa chỉ thư. */
    @Transactional
    public void requestResetByToken(String rawToken, ClientInfo client) {
        throttle(client);
        resetTokens.findByTokenHash(tokens.hash(rawToken))
                .flatMap(token -> accounts.findById(token.getUserId()))
                .filter(UserAccount::canSignIn)
                .ifPresent(account -> issueIfCooledDown(account, client, "BY_EXPIRED_LINK"));
    }

    /** Kiểm đường dẫn ngay khi mở trang, để báo hết hạn trước khi người học gõ mật khẩu mới. */
    @Transactional(noRollbackFor = BusinessException.class)
    public void checkResetToken(String rawToken, ClientInfo client) {
        openToken(rawToken, Instant.now(clock), client);
    }

    /** Đặt mật khẩu mới, đăng xuất mọi thiết bị và mở khóa đăng nhập. Trả về địa chỉ thư để điền sẵn khi đăng nhập. */
    @Transactional(noRollbackFor = BusinessException.class)
    public String resetPassword(String rawToken, String newPassword, ClientInfo client) {
        Instant now = Instant.now(clock);
        PasswordResetToken token = openToken(rawToken, now, client);
        UserAccount account = accounts.findById(token.getUserId())
                .filter(UserAccount::canSignIn)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESET_TOKEN_INVALID));
        passwordPolicy.validate(newPassword, "password");
        if (passwordHasher.matches(newPassword, account.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_REUSED, "password");
        }
        if (resetTokens.markUsed(token.getId(), now) == 0) {
            throw new BusinessException(ErrorCode.RESET_TOKEN_INVALID);
        }
        account = accounts.findById(token.getUserId()).orElseThrow();
        account.changePassword(passwordHasher.hash(newPassword), now);
        resetTokens.invalidateOpenTokens(account.getId(), now);
        int revoked = sessionService.revokeAll(account.getId(), null, SessionService.REASON_PASSWORD_RESET, client);
        loginAttempts.reset(account.getEmail());
        audit.byUser(account.getId(), AuditAction.PASSWORD_RESET_COMPLETED, AuditObjectType.USER_ACCOUNT,
                account.getId(), Map.of("revokedSessions", revoked), client);
        events.publishEvent(new AuthMailNotifier.PasswordChangedNotice(account.getId(), account.getEmail(),
                account.getDisplayName(), now, AuthMailNotifier.PasswordChange.RESET));
        return account.getEmail();
    }

    /**
     * Đổi mật khẩu khi đang đăng nhập. Nhập sai mật khẩu hiện tại được tính như một lần đăng nhập sai,
     * nên kẻ cầm máy người khác không đoán thử được mãi. Xong thì đăng xuất mọi thiết bị khác.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public int changePassword(AuthenticatedUser user, String currentPassword, String newPassword, ClientInfo client) {
        UserAccount account = accounts.findById(user.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        String email = account.getEmail();
        loginAttempts.ensureNotLocked(email, account.getId(), CHANGE_CONTEXT, client);
        if (!passwordHasher.matches(currentPassword == null ? "" : currentPassword, account.getPasswordHash())) {
            throw loginAttempts.recordFailure(email, Optional.of(account), CHANGE_CONTEXT,
                    ErrorCode.CURRENT_PASSWORD_INVALID, "currentPassword", client);
        }
        loginAttempts.reset(email);
        passwordPolicy.validate(newPassword, "newPassword");
        if (passwordHasher.matches(newPassword, account.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_REUSED, "newPassword");
        }
        Instant now = Instant.now(clock);
        account.changePassword(passwordHasher.hash(newPassword), now);
        resetTokens.invalidateOpenTokens(account.getId(), now);
        int revoked = sessionService.revokeAll(account.getId(), user.sessionId(),
                SessionService.REASON_PASSWORD_CHANGED, client);
        audit.byUser(account.getId(), AuditAction.PASSWORD_CHANGED, AuditObjectType.USER_ACCOUNT, account.getId(),
                Map.of("revokedSessions", revoked), client);
        events.publishEvent(new AuthMailNotifier.PasswordChangedNotice(account.getId(), email, account.getDisplayName(),
                now, AuthMailNotifier.PasswordChange.CHANGED));
        return revoked;
    }

    /**
     * Đặt mật khẩu lần đầu cho tài khoản tạo qua nhà cung cấp định danh (NCL-01-CN-007). Sau đó người học
     * đăng nhập được bằng thư điện tử và gỡ được liên kết. Tài khoản đã có mật khẩu phải dùng "đổi mật khẩu".
     */
    @Transactional
    public void setInitialPassword(AuthenticatedUser user, String newPassword, ClientInfo client) {
        UserAccount account = accounts.findById(user.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        if (account.hasPassword()) {
            throw new BusinessException(ErrorCode.PASSWORD_ALREADY_SET);
        }
        passwordPolicy.validate(newPassword, "newPassword");
        Instant now = Instant.now(clock);
        account.changePassword(passwordHasher.hash(newPassword), now);
        audit.byUser(account.getId(), AuditAction.PASSWORD_CHANGED, AuditObjectType.USER_ACCOUNT, account.getId(),
                Map.of("initial", true), client);
        events.publishEvent(new AuthMailNotifier.PasswordChangedNotice(account.getId(), account.getEmail(),
                account.getDisplayName(), now, AuthMailNotifier.PasswordChange.SET));
    }

    private PasswordResetToken openToken(String rawToken, Instant now, ClientInfo client) {
        PasswordResetToken token = resetTokens.findByTokenHash(tokens.hash(rawToken))
                .filter(PasswordResetToken::isOpen)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESET_TOKEN_INVALID));
        if (token.isExpired(now)) {
            audit.byAnonymous(AuditAction.PASSWORD_RESET_LINK_EXPIRED, AuditObjectType.USER_ACCOUNT,
                    token.getUserId(), Map.of("expiredAt", token.getExpiresAt().toString()), client);
            throw new BusinessException(ErrorCode.RESET_TOKEN_EXPIRED);
        }
        return token;
    }

    /** Trong một phút sau thư trước thì im lặng bỏ qua, chống dội thư vào hộp thư của chủ tài khoản. */
    private void issueIfCooledDown(UserAccount account, ClientInfo client, String trigger) {
        Instant now = Instant.now(clock);
        boolean coolingDown = resetTokens.findFirstByUserIdOrderByCreatedAtDesc(account.getId())
                .map(last -> now.isBefore(last.getCreatedAt().plus(settings.resendCooldown())))
                .orElse(false);
        if (coolingDown) {
            return;
        }
        resetTokens.invalidateOpenTokens(account.getId(), now);
        String rawToken = tokens.newToken();
        resetTokens.save(PasswordResetToken.issue(account.getId(), tokens.hash(rawToken), now,
                settings.passwordResetTokenTtl()));
        audit.byAnonymous(AuditAction.PASSWORD_RESET_REQUESTED, AuditObjectType.USER_ACCOUNT, account.getId(),
                Map.of("trigger", trigger), client);
        events.publishEvent(new AuthMailNotifier.PasswordResetRequested(account.getEmail(),
                account.getDisplayName(), rawToken));
    }

    private void throttle(ClientInfo client) {
        rateLimiter.check("password-reset:" + client.ipAddress(), settings.passwordResetLimitPerHour(), HOUR);
    }
}
