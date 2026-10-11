package com.learningcompanion.auth.application;

import com.learningcompanion.auth.domain.LoginThrottle;
import com.learningcompanion.auth.domain.LoginThrottleRepository;
import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.exception.GlobalExceptionHandler;
import com.learningcompanion.shared.web.ClientInfo;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đếm số lần nhập sai mật khẩu liên tiếp theo địa chỉ thư (QTN-02), dùng chung cho đăng nhập và
 * cho việc xác nhận mật khẩu hiện tại khi đổi mật khẩu. Đủ số lần thì tạm khóa và báo chủ tài khoản.
 */
@Service
public class LoginAttemptService {

    public static final String REMAINING_ATTEMPTS = "remainingAttempts";

    private final LoginThrottleRepository throttles;
    private final AuditLogger audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final AppProperties.Auth settings;

    public LoginAttemptService(LoginThrottleRepository throttles, AuditLogger audit, ApplicationEventPublisher events,
                               Clock clock, AppProperties properties) {
        this.throttles = throttles;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
        this.settings = properties.auth();
    }

    /** Đang tạm khóa thì ném ACCOUNT_LOCKED kèm số giây còn phải chờ. */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = BusinessException.class)
    public void ensureNotLocked(String email, UUID accountId, String context, ClientInfo client) {
        Instant now = Instant.now(clock);
        Optional<LoginThrottle> throttle = throttles.findById(email);
        if (throttle.isPresent() && throttle.get().isLocked(now)) {
            audit.byAnonymous(AuditAction.LOGIN_FAILED, AuditObjectType.USER_ACCOUNT, accountId,
                    Map.of("reason", "LOCKED", "context", context), client);
            throw locked(throttle.get(), now);
        }
    }

    /**
     * Ghi một lần nhập sai và trả về lỗi cần ném: ACCOUNT_LOCKED nếu vừa đủ số lần, nếu không thì
     * `failure` kèm số lần thử còn lại.
     */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = BusinessException.class)
    public BusinessException recordFailure(String email, Optional<UserAccount> account, String context,
                                           ErrorCode failure, String field, ClientInfo client) {
        Instant now = Instant.now(clock);
        UUID accountId = account.map(UserAccount::getId).orElse(null);
        LoginThrottle throttle = throttles.findById(email).orElseGet(() -> LoginThrottle.start(email, now));
        throttle.recordFailure(now, settings.maxFailedLogins(), settings.lockoutDuration());
        throttles.save(throttle);
        audit.byAnonymous(AuditAction.LOGIN_FAILED, AuditObjectType.USER_ACCOUNT, accountId,
                Map.of("reason", failure.name(), "context", context, "failedCount", throttle.getFailedCount()),
                client);
        if (throttle.isLocked(now)) {
            audit.byAnonymous(AuditAction.LOGIN_LOCKED, AuditObjectType.USER_ACCOUNT, accountId,
                    Map.of("lockedUntil", throttle.getLockedUntil().toString(), "context", context), client);
            account.ifPresent(owner -> events.publishEvent(new AuthMailNotifier.AccountLockedNotice(
                    owner.getEmail(), owner.getDisplayName(), throttle.getLockedUntil())));
            return locked(throttle, now);
        }
        return new BusinessException(failure, field,
                Map.of(REMAINING_ATTEMPTS, throttle.remainingAttempts(settings.maxFailedLogins())));
    }

    /** Mật khẩu đúng hoặc vừa đặt lại: xóa bộ đếm. */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = BusinessException.class)
    public void reset(String email) {
        throttles.findById(email).ifPresent(throttles::delete);
    }

    private static BusinessException locked(LoginThrottle throttle, Instant now) {
        long seconds = Math.max(1, (throttle.remainingLock(now).toMillis() + 999) / 1000);
        return new BusinessException(ErrorCode.ACCOUNT_LOCKED, null,
                Map.of(GlobalExceptionHandler.RETRY_AFTER_SECONDS, seconds));
    }
}
