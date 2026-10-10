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

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

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

    public void markEmailVerified(Instant now) {
        if (status == AccountStatus.PENDING_VERIFICATION) {
            status = AccountStatus.ACTIVE;
        }
        if (emailVerifiedAt == null) {
            emailVerifiedAt = now;
        }
        updatedAt = now;
    }

    public boolean isPendingVerification() {
        return status == AccountStatus.PENDING_VERIFICATION;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
