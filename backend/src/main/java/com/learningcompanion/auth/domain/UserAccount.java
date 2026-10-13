package com.learningcompanion.auth.domain;

import com.learningcompanion.shared.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_accounts")
public class UserAccount {

    @Id
    private UUID id;

    /** Luôn ở dạng chữ thường (EmailNormalizer), cơ sở dữ liệu cũng ràng buộc điều này. */
    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    /** Trống khi tài khoản tạo qua nhà cung cấp định danh và người học chưa tự đặt mật khẩu. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    /** Chỉ có khi tài khoản đang chờ xóa: thời điểm tài khoản bị xóa vĩnh viễn (NCL-01-CN-009). */
    @Column(name = "deletion_scheduled_for")
    private Instant deletionScheduledFor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected UserAccount() {
    }

    /** Tài khoản người học mới luôn ở trạng thái chờ xác thực thư điện tử. */
    public static UserAccount registerLearner(String email, String displayName, String passwordHash, Instant now) {
        UserAccount account = new UserAccount();
        account.id = UUID.randomUUID();
        account.email = email;
        account.displayName = displayName;
        account.passwordHash = passwordHash;
        account.role = Role.LEARNER;
        account.status = AccountStatus.PENDING_VERIFICATION;
        account.createdAt = now;
        account.updatedAt = now;
        return account;
    }

    /**
     * Tài khoản tạo qua nhà cung cấp định danh (NCL-01-CN-007): địa chỉ thư đã được nhà cung cấp xác thực
     * nên hoạt động ngay, chưa có mật khẩu cho tới khi người học tự đặt.
     */
    public static UserAccount registerFromProvider(String email, String displayName, Instant now) {
        UserAccount account = registerLearner(email, displayName, null, now);
        account.markEmailVerified(now);
        return account;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public void markEmailVerified(Instant now) {
        if (status == AccountStatus.PENDING_VERIFICATION) {
            status = AccountStatus.ACTIVE;
        }
        if (emailVerifiedAt == null) {
            emailVerifiedAt = now;
        }
        updatedAt = now;
    }

    public void changePassword(String newPasswordHash, Instant now) {
        passwordHash = newPasswordHash;
        passwordChangedAt = now;
        updatedAt = now;
    }

    public void rename(String newDisplayName, Instant now) {
        displayName = newDisplayName;
        updatedAt = now;
    }

    public boolean isPendingVerification() {
        return status == AccountStatus.PENDING_VERIFICATION;
    }

    /**
     * Đăng nhập được: tài khoản đang hoạt động, hoặc đang chờ xóa (đăng nhập lại để quyết định có hủy yêu cầu xóa
     * không; mọi chức năng khác bị chặn cho tới khi hủy).
     */
    public boolean canSignIn() {
        return status == AccountStatus.ACTIVE || status == AccountStatus.PENDING_DELETION;
    }

    public boolean isPendingDeletion() {
        return status == AccountStatus.PENDING_DELETION;
    }

    public void scheduleDeletion(Instant deleteAt, Instant now) {
        status = AccountStatus.PENDING_DELETION;
        deletionScheduledFor = deleteAt;
        updatedAt = now;
    }

    public void cancelDeletion(Instant now) {
        status = AccountStatus.ACTIVE;
        deletionScheduledFor = null;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public Instant getDeletionScheduledFor() {
        return deletionScheduledFor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
