package com.learningcompanion.admin.application;

import com.learningcompanion.admin.domain.AdminLoginChallenge;
import com.learningcompanion.admin.domain.AdminLoginChallengeRepository;
import com.learningcompanion.admin.domain.TotpSecret;
import com.learningcompanion.admin.domain.TotpSecretRepository;
import com.learningcompanion.auth.application.SessionService;
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
import com.learningcompanion.shared.security.EncryptionService;
import com.learningcompanion.shared.security.Role;
import com.learningcompanion.shared.security.SessionTokenService;
import com.learningcompanion.shared.web.ClientInfo;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bước thứ hai khi quản trị viên đăng nhập (NCL-03-CN-002, QTN-02). Mật khẩu đúng chỉ mở một "bước chờ mã" ngắn hạn;
 * phiên quản trị chỉ được cấp khi nhập đúng mã sáu chữ số của ứng dụng sinh mã. Lần đầu (hoặc sau khi vào bằng mã
 * khôi phục) quản trị viên phải thiết lập ứng dụng sinh mã và nhận mười mã khôi phục mới. Nhập sai mã năm lần liên
 * tiếp thì khóa đăng nhập quản trị mười lăm phút và gửi thư cảnh báo. Mọi bước đều ghi nhật ký.
 */
@Service
public class AdminTwoFactorService {

    /** Khóa đếm số lần nhập sai mã bước hai, tách khỏi bộ đếm sai mật khẩu của cùng địa chỉ thư. */
    static final String THROTTLE_PREFIX = "admin-2fa:";

    private final AdminLoginChallengeRepository challenges;
    private final TotpSecretRepository secrets;
    private final RecoveryCodeService recoveryCodes;
    private final TotpService totp;
    private final EncryptionService encryption;
    private final SessionTokenService tokens;
    private final SessionService sessions;
    private final UserAccountRepository accounts;
    private final LoginThrottleRepository throttles;
    private final AuditLogger audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final AppProperties.Admin settings;

    public AdminTwoFactorService(AdminLoginChallengeRepository challenges, TotpSecretRepository secrets,
                                 RecoveryCodeService recoveryCodes, TotpService totp, EncryptionService encryption,
                                 SessionTokenService tokens, SessionService sessions, UserAccountRepository accounts,
                                 LoginThrottleRepository throttles, AuditLogger audit,
                                 ApplicationEventPublisher events, Clock clock, AppProperties properties) {
        this.challenges = challenges;
        this.secrets = secrets;
        this.recoveryCodes = recoveryCodes;
        this.totp = totp;
        this.encryption = encryption;
        this.tokens = tokens;
        this.sessions = sessions;
        this.accounts = accounts;
        this.throttles = throttles;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
        this.settings = properties.admin();
    }

    /**
     * Mật khẩu quản trị vừa đúng: mở bước chờ mã. Đang bị khóa vì nhập sai mã thì từ chối luôn, không để dò tiếp.
     * Chưa thiết lập ứng dụng sinh mã thì bước này là thiết lập.
     */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = BusinessException.class)
    public OpenedChallenge open(UserAccount admin, ClientInfo client) {
        Instant now = Instant.now(clock);
        ensureNotLocked(admin, now, client);
        AdminLoginChallenge.Stage stage = secrets.existsById(admin.getId())
                ? AdminLoginChallenge.Stage.VERIFY : AdminLoginChallenge.Stage.ENROLL;
        String rawToken = tokens.newToken();
        AdminLoginChallenge challenge = challenges.save(AdminLoginChallenge.open(admin.getId(), tokens.hash(rawToken),
                stage, now, now.plus(settings.challengeTtl())));
        audit.byAnonymous(AuditAction.ADMIN_TWO_FACTOR_CHALLENGED, AuditObjectType.USER_ACCOUNT, admin.getId(),
                Map.of("stage", stage.name(), "challengeId", challenge.getId().toString()), client);
        return new OpenedChallenge(rawToken, stage, settings.challengeTtl());
    }

    @Transactional(readOnly = true)
    public ChallengeStatus status(String rawToken) {
        AdminLoginChallenge challenge = openChallenge(rawToken, Instant.now(clock));
        UserAccount admin = admin(challenge);
        return new ChallengeStatus(challenge.getStage(), admin.getEmail(), challenge.getExpiresAt());
    }

    /** Tạo khóa mới cho ứng dụng sinh mã; gọi lại thì trả lại đúng khóa đang chờ để mã QR không đổi giữa chừng. */
    @Transactional
    public Enrollment startEnrollment(String rawToken) {
        AdminLoginChallenge challenge = openChallenge(rawToken, Instant.now(clock));
        if (challenge.getStage() != AdminLoginChallenge.Stage.ENROLL) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_CHALLENGE_INVALID);
        }
        byte[] secret;
        if (challenge.getPendingSecretCiphertext() == null) {
            secret = totp.newSecret();
            challenge.holdPendingSecret(encryption.encrypt(secret));
        } else {
            secret = encryption.decrypt(challenge.getPendingSecretCiphertext());
        }
        UserAccount admin = admin(challenge);
        return new Enrollment(TotpService.base32(secret),
                totp.provisioningUri(settings.totpIssuer(), admin.getEmail(), secret));
    }

    /** Mã đầu tiên khớp khóa đang chờ: lưu khóa, cấp mười mã khôi phục mới và mở phiên quản trị. */
    @Transactional(noRollbackFor = BusinessException.class)
    public Completed confirmEnrollment(String rawToken, String code, String rawDeviceId, ClientInfo client) {
        Instant now = Instant.now(clock);
        AdminLoginChallenge challenge = openChallenge(rawToken, now);
        if (challenge.getStage() != AdminLoginChallenge.Stage.ENROLL) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_CHALLENGE_INVALID);
        }
        if (challenge.getPendingSecretCiphertext() == null) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_ENROLLMENT_NOT_STARTED);
        }
        UserAccount admin = admin(challenge);
        ensureNotLocked(admin, now, client);
        byte[] secret = encryption.decrypt(challenge.getPendingSecretCiphertext());
        OptionalLong step = totp.verify(secret, code, now, 0);
        if (step.isEmpty()) {
            throw failure(admin, ErrorCode.TWO_FACTOR_CODE_INVALID, "ENROLL", now, client);
        }
        Optional<TotpSecret> existing = secrets.findById(admin.getId());
        if (existing.isPresent()) {
            existing.get().replace(challenge.getPendingSecretCiphertext(), step.getAsLong(), now);
        } else {
            secrets.save(TotpSecret.enroll(admin.getId(), challenge.getPendingSecretCiphertext(), step.getAsLong(),
                    now));
        }
        List<String> codes = recoveryCodes.regenerate(admin.getId(), now);
        audit.byUser(admin.getId(), AuditAction.ADMIN_TWO_FACTOR_ENROLLED, AuditObjectType.USER_ACCOUNT,
                admin.getId(), Map.of("recoveryCodes", codes.size(), "replaced", existing.isPresent()), client);
        return complete(challenge, admin, "TOTP_ENROLLMENT", rawDeviceId, now, client, codes);
    }

    /**
     * Mã sáu chữ số đúng: mở phiên quản trị. Nếu lần trước vào bằng mã khôi phục mà chưa thiết lập lại, bước này
     * chuyển sang thiết lập lại thay vì mở phiên.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public Outcome verify(String rawToken, String code, String rawDeviceId, ClientInfo client) {
        Instant now = Instant.now(clock);
        AdminLoginChallenge challenge = openChallenge(rawToken, now);
        if (challenge.getStage() != AdminLoginChallenge.Stage.VERIFY) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_CHALLENGE_INVALID);
        }
        UserAccount admin = admin(challenge);
        ensureNotLocked(admin, now, client);
        TotpSecret secret = secrets.findById(admin.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TWO_FACTOR_CHALLENGE_INVALID));
        OptionalLong step = totp.verify(encryption.decrypt(secret.getSecretCiphertext()), code, now,
                secret.getLastUsedStep());
        if (step.isEmpty()) {
            throw failure(admin, ErrorCode.TWO_FACTOR_CODE_INVALID, "TOTP", now, client);
        }
        secret.markUsed(step.getAsLong());
        if (secret.isResetRequired()) {
            resetThrottle(admin);
            challenge.requireEnrollment();
            return Outcome.enrollmentRequired();
        }
        return Outcome.signedIn(complete(challenge, admin, "TOTP", rawDeviceId, now, client, List.of()));
    }

    /**
     * Mất điện thoại sinh mã (TC-03): một mã khôi phục chưa dùng thay cho mã sáu chữ số. Mã bị đánh dấu đã dùng, khóa
     * cũ hết hiệu lực ở lần đăng nhập sau và bước này chuyển sang thiết lập lại ứng dụng sinh mã ngay.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public Outcome recover(String rawToken, String recoveryCode, ClientInfo client) {
        Instant now = Instant.now(clock);
        AdminLoginChallenge challenge = openChallenge(rawToken, now);
        if (challenge.getStage() != AdminLoginChallenge.Stage.VERIFY) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_CHALLENGE_INVALID);
        }
        UserAccount admin = admin(challenge);
        ensureNotLocked(admin, now, client);
        if (!recoveryCodes.redeem(admin.getId(), recoveryCode, now)) {
            throw failure(admin, ErrorCode.RECOVERY_CODE_INVALID, "RECOVERY_CODE", now, client);
        }
        resetThrottle(admin);
        secrets.findById(admin.getId()).ifPresent(TotpSecret::requireReset);
        challenge.requireEnrollment();
        audit.byAnonymous(AuditAction.ADMIN_RECOVERY_CODE_USED, AuditObjectType.USER_ACCOUNT, admin.getId(),
                Map.of("remaining", recoveryCodes.remaining(admin.getId())), client);
        return Outcome.enrollmentRequired();
    }

    private Completed complete(AdminLoginChallenge challenge, UserAccount admin, String method, String rawDeviceId,
                               Instant now, ClientInfo client, List<String> codes) {
        challenge.complete(now);
        resetThrottle(admin);
        SessionService.StartedSession session = sessions.startTwoFactor(admin, client, rawDeviceId, "ADMIN_LOGIN");
        audit.byUser(admin.getId(), AuditAction.LOGIN_SUCCEEDED, AuditObjectType.SESSION, session.sessionId(),
                Map.of("method", method, "twoFactor", true,
                        "maxLifetimeHours", settings.sessionMaxLifetime().toHours()), client);
        return new Completed(admin, session, now.plus(settings.sessionMaxLifetime()), codes);
    }

    private void ensureNotLocked(UserAccount admin, Instant now, ClientInfo client) {
        Optional<LoginThrottle> throttle = throttles.findById(throttleKey(admin));
        if (throttle.isPresent() && throttle.get().isLocked(now)) {
            audit.byAnonymous(AuditAction.LOGIN_FAILED, AuditObjectType.USER_ACCOUNT, admin.getId(),
                    Map.of("reason", "ADMIN_LOCKED", "context", "ADMIN_TWO_FACTOR"), client);
            throw locked(throttle.get(), now);
        }
    }

    private BusinessException failure(UserAccount admin, ErrorCode code, String method, Instant now,
                                      ClientInfo client) {
        String key = throttleKey(admin);
        LoginThrottle throttle = throttles.findById(key).orElseGet(() -> LoginThrottle.start(key, now));
        throttle.recordFailure(now, settings.maxFailedCodes(), settings.lockoutDuration());
        throttles.save(throttle);
        audit.byAnonymous(AuditAction.ADMIN_TWO_FACTOR_FAILED, AuditObjectType.USER_ACCOUNT, admin.getId(),
                Map.of("method", method, "failedCount", throttle.getFailedCount()), client);
        if (throttle.isLocked(now)) {
            audit.byAnonymous(AuditAction.ADMIN_TWO_FACTOR_LOCKED, AuditObjectType.USER_ACCOUNT, admin.getId(),
                    Map.of("lockedUntil", throttle.getLockedUntil().toString(),
                            "failedCount", throttle.getFailedCount()), client);
            events.publishEvent(new AdminSecurityMail.AdminLoginLockedNotice(admin.getEmail(),
                    admin.getDisplayName(), throttle.getLockedUntil(), throttle.getFailedCount(),
                    client.ipAddress()));
            return locked(throttle, now);
        }
        return new BusinessException(code, "code", Map.of("remainingAttempts",
                throttle.remainingAttempts(settings.maxFailedCodes())));
    }

    private void resetThrottle(UserAccount admin) {
        throttles.findById(throttleKey(admin)).ifPresent(throttles::delete);
    }

    private static String throttleKey(UserAccount admin) {
        return THROTTLE_PREFIX + admin.getEmail();
    }

    private static BusinessException locked(LoginThrottle throttle, Instant now) {
        Duration remaining = throttle.remainingLock(now);
        long seconds = Math.max(1, (remaining.toMillis() + 999) / 1000);
        return new BusinessException(ErrorCode.ADMIN_LOGIN_LOCKED, null,
                Map.of(GlobalExceptionHandler.RETRY_AFTER_SECONDS, seconds));
    }

    private AdminLoginChallenge openChallenge(String rawToken, Instant now) {
        return Optional.ofNullable(rawToken)
                .filter(value -> !value.isBlank() && value.length() <= 128)
                .flatMap(value -> challenges.findByTokenHash(tokens.hash(value)))
                .filter(challenge -> challenge.isOpen(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.TWO_FACTOR_CHALLENGE_INVALID));
    }

    private UserAccount admin(AdminLoginChallenge challenge) {
        return accounts.findById(challenge.getAccountId())
                .filter(account -> account.getRole() == Role.ADMIN && account.canSignIn())
                .orElseThrow(() -> new BusinessException(ErrorCode.TWO_FACTOR_CHALLENGE_INVALID));
    }

    public record OpenedChallenge(String rawToken, AdminLoginChallenge.Stage stage, Duration lifetime) {
    }

    public record ChallengeStatus(AdminLoginChallenge.Stage stage, String email, Instant expiresAt) {
    }

    public record Enrollment(String secret, String otpauthUri) {
    }

    public record Completed(UserAccount account, SessionService.StartedSession session, Instant sessionExpiresAt,
                            List<String> recoveryCodes) {
    }

    /** Kết quả bước nhập mã: đã mở phiên, hoặc còn phải thiết lập lại ứng dụng sinh mã. */
    public record Outcome(Completed completed) {

        static Outcome signedIn(Completed completed) {
            return new Outcome(completed);
        }

        static Outcome enrollmentRequired() {
            return new Outcome(null);
        }

        public boolean isEnrollmentRequired() {
            return completed == null;
        }
    }
}
