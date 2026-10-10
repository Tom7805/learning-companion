package com.learningcompanion.auth.application;

import com.learningcompanion.auth.domain.AccountStatus;
import com.learningcompanion.auth.domain.LoginThrottle;
import com.learningcompanion.auth.domain.LoginThrottleRepository;
import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.exception.GlobalExceptionHandler;
import com.learningcompanion.shared.security.PasswordHasher;
import com.learningcompanion.shared.security.RateLimiter;
import com.learningcompanion.shared.web.ClientInfo;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đăng nhập bằng thư điện tử và mật khẩu (NCL-01-CN-002). Nhập sai liên tiếp đủ số lần thì tạm khóa;
 * địa chỉ chưa đăng ký cũng được đếm và băm mật khẩu như thường, nên phản hồi không tiết lộ tài khoản.
 */
@Service
public class AuthenticationService {

    private static final Duration HOUR = Duration.ofHours(1);
    private static final String ATTEMPTS_LEFT = "remainingAttempts";

    private final UserAccountRepository accounts;
    private final LoginThrottleRepository throttles;
    private final PasswordHasher passwordHasher;
    private final SessionService sessionService;
    private final RateLimiter rateLimiter;
    private final AuditLogger audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final AppProperties.Auth settings;
    private final String dummyHash;

    public AuthenticationService(UserAccountRepository accounts, LoginThrottleRepository throttles,
                                 PasswordHasher passwordHasher, SessionService sessionService,
                                 RateLimiter rateLimiter, AuditLogger audit, ApplicationEventPublisher events,
                                 Clock clock, AppProperties properties) {
        this.accounts = accounts;
        this.throttles = throttles;
        this.passwordHasher = passwordHasher;
        this.sessionService = sessionService;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
        this.settings = properties.auth();
        // Băm sẵn một mật khẩu giả để địa chỉ chưa đăng ký cũng tốn cùng thời gian kiểm tra.
        this.dummyHash = passwordHasher.hash(UUID.randomUUID().toString());
    }

    /** Lỗi nghiệp vụ không hủy giao dịch, để số lần nhập sai và nhật ký vẫn được ghi. */
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResult login(LoginCommand command, ClientInfo client) {
        rateLimiter.check("login:" + client.ipAddress(), settings.loginLimitPerHour(), HOUR);
        Instant now = Instant.now(clock);
        String email = EmailNormalizer.normalize(command.email());
        Optional<UserAccount> account = accounts.findByEmail(email);
        UUID accountId = account.map(UserAccount::getId).orElse(null);
        LoginThrottle throttle = throttles.findById(email).orElseGet(() -> LoginThrottle.start(email, now));

        if (throttle.isLocked(now)) {
            audit.byAnonymous(AuditAction.LOGIN_FAILED, AuditObjectType.USER_ACCOUNT, accountId,
                    Map.of("reason", "LOCKED"), client);
            throw locked(throttle, now);
        }

        String hash = account.map(UserAccount::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = passwordHasher.matches(command.password() == null ? "" : command.password(), hash);
        if (account.isEmpty() || !passwordMatches) {
            throttle.recordFailure(now, settings.maxFailedLogins(), settings.lockoutDuration());
            throttles.save(throttle);
            audit.byAnonymous(AuditAction.LOGIN_FAILED, AuditObjectType.USER_ACCOUNT, accountId,
                    Map.of("reason", "INVALID_CREDENTIALS", "failedCount", throttle.getFailedCount()), client);
            if (throttle.isLocked(now)) {
                audit.byAnonymous(AuditAction.LOGIN_LOCKED, AuditObjectType.USER_ACCOUNT, accountId,
                        Map.of("lockedUntil", throttle.getLockedUntil().toString()), client);
                account.ifPresent(owner -> events.publishEvent(new AuthMailNotifier.AccountLockedNotice(
                        owner.getEmail(), owner.getDisplayName(), throttle.getLockedUntil())));
                throw locked(throttle, now);
            }
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, null,
                    Map.of(ATTEMPTS_LEFT, throttle.remainingAttempts(settings.maxFailedLogins())));
        }

        // Mật khẩu đúng: xóa bộ đếm rồi mới xét trạng thái tài khoản.
        throttles.findById(email).ifPresent(throttles::delete);
        UserAccount owner = account.get();
        if (owner.getStatus() == AccountStatus.PENDING_VERIFICATION) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (owner.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_UNAVAILABLE);
        }
        SessionService.StartedSession session = sessionService.start(owner, client, command.rememberDevice(),
                command.deviceId(), "LOGIN");
        audit.byUser(owner.getId(), AuditAction.LOGIN_SUCCEEDED, AuditObjectType.SESSION, session.sessionId(),
                Map.of("rememberDevice", command.rememberDevice()), client);
        return new LoginResult(owner, session);
    }

    private BusinessException locked(LoginThrottle throttle, Instant now) {
        long seconds = Math.max(1, (throttle.remainingLock(now).toMillis() + 999) / 1000);
        return new BusinessException(ErrorCode.ACCOUNT_LOCKED, null,
                Map.of(GlobalExceptionHandler.RETRY_AFTER_SECONDS, seconds));
    }

    public record LoginCommand(String email, String password, boolean rememberDevice, String deviceId) {
    }

    public record LoginResult(UserAccount account, SessionService.StartedSession session) {
    }
}
