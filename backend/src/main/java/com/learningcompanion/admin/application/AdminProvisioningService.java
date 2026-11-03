package com.learningcompanion.admin.application;

import com.learningcompanion.auth.application.EmailNormalizer;
import com.learningcompanion.auth.application.PasswordPolicyValidator;
import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.security.PasswordHasher;
import com.learningcompanion.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo tài khoản quản trị viên (NCL-03-CN-002). Không có giao diện hay điểm cuối nào gọi tới đây: chỉ câu lệnh cài đặt
 * chạy trên máy chủ ({@link AdminBootstrapCommand}). Hệ thống chỉ có một quản trị viên; yêu cầu tạo thêm luôn bị từ
 * chối và ghi nhật ký (TC-04). Cơ sở dữ liệu cũng ràng buộc điều này bằng chỉ mục duy nhất.
 */
@Service
public class AdminProvisioningService {

    private final UserAccountRepository accounts;
    private final PasswordPolicyValidator passwordPolicy;
    private final PasswordHasher passwordHasher;
    private final AuditLogger audit;
    private final Clock clock;

    public AdminProvisioningService(UserAccountRepository accounts, PasswordPolicyValidator passwordPolicy,
                                    PasswordHasher passwordHasher, AuditLogger audit, Clock clock) {
        this.accounts = accounts;
        this.passwordPolicy = passwordPolicy;
        this.passwordHasher = passwordHasher;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public UUID createAdmin(String rawEmail, String displayName, String password) {
        String email = EmailNormalizer.normalize(rawEmail);
        rejectIfAdminExists(email, "BOOTSTRAP_COMMAND");
        if (email.isBlank() || !email.contains("@")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "email");
        }
        String name = displayName == null || displayName.isBlank() ? "Quản trị viên" : displayName.trim();
        if (name.length() > 50) {
            throw new BusinessException(ErrorCode.DISPLAY_NAME_TOO_LONG, "displayName");
        }
        if (accounts.findByEmail(email).isPresent()) {
            // Không biến tài khoản người học sẵn có thành quản trị viên: hai vai trò tách hẳn nhau (QTN-01).
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "email",
                    Map.of("reason", "EMAIL_IN_USE"));
        }
        passwordPolicy.validate(password, "password");
        UserAccount admin = UserAccount.createAdmin(email, name, passwordHasher.hash(password), Instant.now(clock));
        try {
            accounts.saveAndFlush(admin);
        } catch (DataIntegrityViolationException raced) {
            // Hai câu lệnh chạy cùng lúc: chỉ mục duy nhất giữ lại một quản trị viên.
            throw new BusinessException(ErrorCode.ADMIN_ALREADY_EXISTS);
        }
        audit.bySystem(AuditAction.ADMIN_ACCOUNT_CREATED, AuditObjectType.USER_ACCOUNT, admin.getId(),
                Map.of("method", "BOOTSTRAP_COMMAND"));
        return admin.getId();
    }

    /** Từ chối khi đã có quản trị viên; ghi nhật ký lần từ chối kèm nơi gửi yêu cầu. */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = BusinessException.class)
    public void rejectIfAdminExists(String requestedEmail, String source) {
        accounts.findFirstByRole(Role.ADMIN).ifPresent(existing -> {
            audit.bySystem(AuditAction.ADMIN_CREATION_REJECTED, AuditObjectType.USER_ACCOUNT, existing.getId(),
                    Map.of("source", source, "reason", "ADMIN_ALREADY_EXISTS",
                            "requestedEmail", requestedEmail == null ? "" : requestedEmail));
            throw new BusinessException(ErrorCode.ADMIN_ALREADY_EXISTS);
        });
    }
}
