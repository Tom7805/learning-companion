package com.learningcompanion.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Khóa bí mật của ứng dụng sinh mã đã thiết lập cho quản trị viên (NCL-03-CN-002), lưu dạng mã hóa. Bước thời gian của
 * mã dùng gần nhất được giữ lại để một mã đã dùng không dùng lại được trong cùng khoảng ba mươi giây.
 */
@Entity
@Table(name = "admin_totp_secrets")
public class TotpSecret {

    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "secret_ciphertext", nullable = false)
    private byte[] secretCiphertext;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    @Column(name = "last_used_step", nullable = false)
    private long lastUsedStep;

    /** Vừa đăng nhập bằng mã khôi phục: lần sau phải thiết lập lại ứng dụng sinh mã. */
    @Column(name = "reset_required", nullable = false)
    private boolean resetRequired;

    protected TotpSecret() {
    }

    public static TotpSecret enroll(UUID accountId, byte[] secretCiphertext, long usedStep, Instant now) {
        TotpSecret secret = new TotpSecret();
        secret.accountId = accountId;
        secret.secretCiphertext = secretCiphertext;
        secret.enrolledAt = now;
        secret.lastUsedStep = usedStep;
        return secret;
    }

    /** Thay bằng khóa mới khi thiết lập lại; mọi mã của ứng dụng cũ hết tác dụng. */
    public void replace(byte[] newCiphertext, long usedStep, Instant now) {
        secretCiphertext = newCiphertext;
        enrolledAt = now;
        lastUsedStep = usedStep;
        resetRequired = false;
    }

    public void markUsed(long step) {
        lastUsedStep = step;
    }

    public void requireReset() {
        resetRequired = true;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public byte[] getSecretCiphertext() {
        return secretCiphertext;
    }

    public long getLastUsedStep() {
        return lastUsedStep;
    }

    public boolean isResetRequired() {
        return resetRequired;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }
}
