package com.learningcompanion.auth.application;

import com.learningcompanion.auth.domain.AccountStatus;
import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.security.PasswordHasher;
import com.learningcompanion.shared.security.RateLimiter;
import com.learningcompanion.shared.web.ClientInfo;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đăng nhập bằng thư điện tử và mật khẩu (NCL-01-CN-002). Nhập sai liên tiếp đủ số lần thì tạm khóa;
 * địa chỉ chưa đăng ký cũng được đếm và băm mật khẩu như thường, nên phản hồi không tiết lộ tài khoản.
 */
@Service
public class AuthenticationService {

    private static final Duration HOUR = Duration.ofHours(1);
    private static final String CONTEXT = "LOGIN";

    private final UserAccountRepository accounts;
    private final LoginAttemptService loginAttempts;
    private final PasswordHasher passwordHasher;
    private final SessionService sessionService;
    private final RateLimiter rateLimiter;
    private final AuditLogger audit;
    private final AppProperties.Auth settings;
    private final String dummyHash;

    public AuthenticationService(UserAccountRepository accounts, LoginAttemptService loginAttempts,
                                 PasswordHasher passwordHasher, SessionService sessionService,
                                 RateLimiter rateLimiter, AuditLogger audit, AppProperties properties) {
        this.accounts = accounts;
        this.loginAttempts = loginAttempts;
        this.passwordHasher = passwordHasher;
        this.sessionService = sessionService;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.settings = properties.auth();
        // Băm sẵn một mật khẩu giả để địa chỉ chưa đăng ký cũng tốn cùng thời gian kiểm tra.
        this.dummyHash = passwordHasher.hash(UUID.randomUUID().toString());
    }

    /** Lỗi nghiệp vụ không hủy giao dịch, để số lần nhập sai và nhật ký vẫn được ghi. */
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResult login(LoginCommand command, ClientInfo client) {
        rateLimiter.check("login:" + client.ipAddress(), settings.loginLimitPerHour(), HOUR);
        String email = EmailNormalizer.normalize(command.email());
        Optional<UserAccount> account = accounts.findByEmail(email);
        loginAttempts.ensureNotLocked(email, account.map(UserAccount::getId).orElse(null), CONTEXT, client);

        // Tài khoản chưa có mật khẩu vẫn so với mã băm giả để thời gian phản hồi như mọi lần nhập sai.
        String hash = account.map(UserAccount::getPasswordHash).orElse(dummyHash);
        if (hash == null) {
            hash = dummyHash;
        }
        boolean passwordMatches = passwordHasher.matches(command.password() == null ? "" : command.password(), hash)
                && account.map(UserAccount::hasPassword).orElse(false);
        if (account.isEmpty() || !passwordMatches) {
            throw loginAttempts.recordFailure(email, account, CONTEXT, ErrorCode.INVALID_CREDENTIALS, null, client);
        }

        // Mật khẩu đúng: xóa bộ đếm rồi mới xét trạng thái tài khoản.
        loginAttempts.reset(email);
        UserAccount owner = account.get();
        if (owner.getStatus() == AccountStatus.PENDING_VERIFICATION) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (!owner.canSignIn()) {
            throw new BusinessException(ErrorCode.ACCOUNT_UNAVAILABLE);
        }
        SessionService.StartedSession session = sessionService.start(owner, client, command.rememberDevice(),
                command.deviceId(), "LOGIN");
        audit.byUser(owner.getId(), AuditAction.LOGIN_SUCCEEDED, AuditObjectType.SESSION, session.sessionId(),
                Map.of("rememberDevice", command.rememberDevice()), client);
        return new LoginResult(owner, session);
    }

    public record LoginCommand(String email, String password, boolean rememberDevice, String deviceId) {
    }

    public record LoginResult(UserAccount account, SessionService.StartedSession session) {
    }
}
