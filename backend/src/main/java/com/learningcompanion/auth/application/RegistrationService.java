package com.learningcompanion.auth.application;

import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.privacy.application.ConsentService;
import com.learningcompanion.privacy.application.TermsVersionService;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.security.PasswordHasher;
import com.learningcompanion.shared.security.RateLimiter;
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
 * Đăng ký tài khoản người học (NCL-01-CN-001).
 * Mọi nhánh đều trả cùng một kết quả và cùng tốn công băm mật khẩu, nên người ngoài không dò được
 * địa chỉ thư nào đã có tài khoản.
 */
@Service
public class RegistrationService {

    private static final Duration HOUR = Duration.ofHours(1);

    private final UserAccountRepository accounts;
    private final PasswordPolicyValidator passwordPolicy;
    private final PasswordHasher passwordHasher;
    private final TermsVersionService termsVersionService;
    private final ConsentService consentService;
    private final EmailVerificationService emailVerificationService;
    private final RateLimiter rateLimiter;
    private final AuditLogger audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final AppProperties.Auth settings;

    public RegistrationService(UserAccountRepository accounts, PasswordPolicyValidator passwordPolicy,
                               PasswordHasher passwordHasher, TermsVersionService termsVersionService,
                               ConsentService consentService, EmailVerificationService emailVerificationService,
                               RateLimiter rateLimiter, AuditLogger audit, ApplicationEventPublisher events,
                               Clock clock, AppProperties properties) {
        this.accounts = accounts;
        this.passwordPolicy = passwordPolicy;
        this.passwordHasher = passwordHasher;
        this.termsVersionService = termsVersionService;
        this.consentService = consentService;
        this.emailVerificationService = emailVerificationService;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
        this.settings = properties.auth();
    }

    /** Trả về địa chỉ thư đã chuẩn hóa để giao diện hiển thị trên màn hình kiểm tra hộp thư. */
    @Transactional
    public String register(RegisterCommand command, ClientInfo client) {
        rateLimiter.check("register:" + client.ipAddress(), settings.registerLimitPerHour(), HOUR);
        if (!command.acceptTerms()) {
            throw new BusinessException(ErrorCode.TERMS_NOT_ACCEPTED, "acceptTerms");
        }
        TermsVersionService.CurrentTerms current = termsVersionService.current();
        if (!current.terms().getVersion().equals(command.termsVersion())
                || !current.privacy().getVersion().equals(command.privacyVersion())) {
            throw new BusinessException(ErrorCode.TERMS_VERSION_OUTDATED, "acceptTerms");
        }
        passwordPolicy.validate(command.password());

        String email = EmailNormalizer.normalize(command.email());
        String displayName = normalizeDisplayName(command.displayName());
        String passwordHash = passwordHasher.hash(command.password());

        Optional<UserAccount> existing = accounts.findByEmail(email);
        if (existing.isEmpty()) {
            createAccount(email, displayName, passwordHash, current, client);
        } else if (existing.get().isPendingVerification()) {
            // Có thể chính người học đăng ký lại vì chưa thấy thư: gửi lại đường dẫn, giữ nguyên mật khẩu cũ.
            emailVerificationService.resendIfCooledDown(existing.get(), client, "REGISTER_AGAIN");
        } else {
            notifyOwner(existing.get(), client);
        }
        return email;
    }

    private void createAccount(String email, String displayName, String passwordHash,
                               TermsVersionService.CurrentTerms current, ClientInfo client) {
        UserAccount account = accounts.save(
                UserAccount.registerLearner(email, displayName, passwordHash, Instant.now(clock)));
        audit.byUser(account.getId(), AuditAction.ACCOUNT_REGISTERED, AuditObjectType.USER_ACCOUNT,
                account.getId(), Map.of("role", account.getRole().name(), "status", account.getStatus().name()),
                client);
        consentService.recordRegistrationConsents(account.getId(), current.terms().getVersion(),
                current.privacy().getVersion(), client);
        emailVerificationService.sendInitial(account, client);
    }

    private void notifyOwner(UserAccount owner, ClientInfo client) {
        audit.byAnonymous(AuditAction.REGISTRATION_ATTEMPT_EXISTING_EMAIL, AuditObjectType.USER_ACCOUNT,
                owner.getId(), Map.of(), client);
        // Chặn dùng biểu mẫu đăng ký để dội thư vào hộp thư của chủ tài khoản.
        if (rateLimiter.tryAcquire("registration-attempt-notice:" + owner.getId(), 1,
                settings.registrationAttemptNoticeInterval())) {
            events.publishEvent(new AuthMailNotifier.RegistrationAttemptNoticeRequested(
                    owner.getEmail(), owner.getDisplayName(), Instant.now(clock)));
        }
    }

    static String normalizeDisplayName(String displayName) {
        return displayName == null ? "" : displayName.strip().replaceAll("\\s+", " ");
    }

    public record RegisterCommand(String displayName, String email, String password, boolean acceptTerms,
                                  String termsVersion, String privacyVersion) {
    }
}
