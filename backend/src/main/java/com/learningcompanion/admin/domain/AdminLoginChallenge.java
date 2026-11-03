package com.learningcompanion.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Bước thứ hai của một lần đăng nhập quản trị (NCL-03-CN-002): mật khẩu đã đúng, trình duyệt giữ chuỗi bí mật của
 * bước này trong cookie HttpOnly và còn phải nhập mã sáu chữ số ({@link Stage#VERIFY}) hoặc thiết lập ứng dụng sinh
 * mã ({@link Stage#ENROLL}). Hết hạn sau vài phút và chỉ hoàn tất được một lần.
 */
@Entity
@Table(name = "admin_login_challenges")
public class AdminLoginChallenge {

    public enum Stage { VERIFY, ENROLL }

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Stage stage;

    /** Khóa đang chờ quản trị viên quét và xác nhận bằng mã đầu tiên. */
    @Column(name = "pending_secret_ciphertext")
    private byte[] pendingSecretCiphertext;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AdminLoginChallenge() {
    }

    public static AdminLoginChallenge open(UUID accountId, String tokenHash, Stage stage,
                                           Instant now, Instant expiresAt) {
        AdminLoginChallenge challenge = new AdminLoginChallenge();
        challenge.id = UUID.randomUUID();
        challenge.accountId = accountId;
        challenge.tokenHash = tokenHash;
        challenge.stage = stage;
        challenge.createdAt = now;
        challenge.expiresAt = expiresAt;
        return challenge;
    }

    public boolean isOpen(Instant now) {
        return completedAt == null && now.isBefore(expiresAt);
    }

    /** Vào bằng mã khôi phục: bước này chuyển sang thiết lập lại ứng dụng sinh mã. */
    public void requireEnrollment() {
        stage = Stage.ENROLL;
        pendingSecretCiphertext = null;
    }

    public void holdPendingSecret(byte[] ciphertext) {
        pendingSecretCiphertext = ciphertext;
    }

    public void complete(Instant now) {
        completedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public Stage getStage() {
        return stage;
    }

    public byte[] getPendingSecretCiphertext() {
        return pendingSecretCiphertext;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
