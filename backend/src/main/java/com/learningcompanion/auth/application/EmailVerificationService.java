package com.learningcompanion.auth.application;

import com.learningcompanion.auth.domain.AccountStatus;
import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.auth.domain.VerificationToken;
import com.learningcompanion.auth.domain.VerificationTokenRepository;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.security.RateLimiter;
import com.learningcompanion.shared.security.SessionTokenService;
import com.learningcompanion.shared.web.ClientInfo;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phát hành, gửi lại và xác minh đường dẫn xác thực thư điện tử.
 * Gửi lại luôn trả cùng một kết quả để không lộ địa chỉ nào đã đăng ký.
 */
@Service
public class EmailVerificationService {

    private static final Duration HOUR = Duration.ofHours(1);

    private final UserAccountRepository accounts;
    private final VerificationTokenRepository verificationTokens;
    private final SessionTokenService tokens;
    private final SessionService sessionService;
    private final RateLimiter rateLimiter;
    private final AuditLogger audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final AppProperties.Auth settings;

    public EmailVerificationService(UserAccountRepository accounts, VerificationTokenRepository verificationTokens,
                                    SessionTokenService tokens, SessionService sessionService,
                                    RateLimiter rateLimiter, AuditLogger audit, ApplicationEventPublisher events,
                                    Clock clock, AppProperties properties) {
        this.accounts = accounts;
        this.verificationTokens = verificationTokens;
        this.tokens = tokens;
        this.sessionService = sessionService;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
        this.settings = properties.auth();
    }

    /** Thư đầu tiên ngay khi đăng ký. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void sendInitial(UserAccount account, ClientInfo client) {
        issue(account);
        audit.byUser(account.getId(), AuditAction.VERIFICATION_EMAIL_SENT, AuditObjectType.USER_ACCOUNT,
                account.getId(), Map.of(), client);
    }

    /** Gửi lại cho tài khoản chờ xác thực nếu đã qua thời gian chờ; trong thời gian chờ thì im lặng bỏ qua. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void resendIfCooledDown(UserAccount account, ClientInfo client, String trigger) {
        Instant now = Instant.now(clock);
        boolean coolingDown = verificationTokens.findFirstByUserIdOrderByCreatedAtDesc(account.getId())
                .map(last -> now.isBefore(last.getCreatedAt().plus(settings.resendCooldown())))
                .orElse(false);
        if (coolingDown) {
            return;
        }
        issue(account);
        audit.byAnonymous(AuditAction.VERIFICATION_EMAIL_RESENT, AuditObjectType.USER_ACCOUNT, account.getId(),
                Map.of("trigger", trigger), client);
    }

    @Transactional
    public void resendByEmail(String email, ClientInfo client) {
        rateLimiter.check("verify-resend:" + client.ipAddress(), settings.resendLimitPerHour(), HOUR);
        accounts.findByEmail(EmailNormalizer.normalize(email))
                .filter(UserAccount::isPendingVerification)
                .ifPresent(account -> resendIfCooledDown(account, client, "RESEND_BY_EMAIL"));
    }

    /** Gửi lại từ trang báo đường dẫn hết hạn, không bắt người học nhập lại địa chỉ thư. */
    @Transactional
    public void resendByToken(String rawToken, ClientInfo client) {
        rateLimiter.check("verify-resend:" + client.ipAddress(), settings.resendLimitPerHour(), HOUR);
        verificationTokens.findByTokenHash(tokens.hash(rawToken))
                .flatMap(token -> accounts.findById(token.getUserId()))
                .filter(UserAccount::isPendingVerification)
                .ifPresent(account -> resendIfCooledDown(account, client, "RESEND_BY_EXPIRED_LINK"));
    }

    /**
     * Kích hoạt tài khoản và mở phiên đăng nhập. Lần bấm đường dẫn hết hạn vẫn được ghi nhật ký
     * nên lỗi nghiệp vụ không hủy giao dịch.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public VerificationResult verify(String rawToken, ClientInfo client) {
        Instant now = Instant.now(clock);
        VerificationToken token = verificationTokens.findByTokenHash(tokens.hash(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_TOKEN_INVALID));
        if (token.isUsed()) {
            throw new BusinessException(ErrorCode.VERIFICATION_TOKEN_USED);
        }
        if (token.isInvalidated()) {
            throw new BusinessException(ErrorCode.VERIFICATION_TOKEN_INVALID);
        }
        if (token.isExpired(now)) {
            audit.byAnonymous(AuditAction.VERIFICATION_LINK_EXPIRED, AuditObjectType.USER_ACCOUNT,
                    token.getUserId(), Map.of("expiredAt", token.getExpiresAt().toString()), client);
            throw new BusinessException(ErrorCode.VERIFICATION_TOKEN_EXPIRED);
        }
        if (verificationTokens.markUsed(token.getId(), now) == 0) {
            throw new BusinessException(ErrorCode.VERIFICATION_TOKEN_USED);
        }
        UserAccount account = accounts.findById(token.getUserId())
                .filter(found -> found.getStatus() == AccountStatus.PENDING_VERIFICATION
                        || found.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_TOKEN_INVALID));
        account.markEmailVerified(now);
        audit.byUser(account.getId(), AuditAction.EMAIL_VERIFIED, AuditObjectType.USER_ACCOUNT, account.getId(),
                Map.of("status", account.getStatus().name()), client);
        SessionService.StartedSession session = sessionService.start(account, client);
        return new VerificationResult(account, session);
    }

    private void issue(UserAccount account) {
        Instant now = Instant.now(clock);
        verificationTokens.invalidateOpenTokens(account.getId(), now);
        String rawToken = tokens.newToken();
        verificationTokens.save(VerificationToken.issue(account.getId(), tokens.hash(rawToken), now,
                settings.verificationTokenTtl()));
        events.publishEvent(new AuthMailNotifier.VerificationMailRequested(
                account.getEmail(), account.getDisplayName(), rawToken));
    }

    public record VerificationResult(UserAccount account, SessionService.StartedSession session) {
    }
}
