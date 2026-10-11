package com.learningcompanion.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Đường dẫn đặt lại mật khẩu dùng một lần, có hạn ngắn; chỉ lưu băm của chuỗi bí mật. */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    protected PasswordResetToken() {
    }

    public static PasswordResetToken issue(UUID userId, String tokenHash, Instant now, Duration ttl) {
        PasswordResetToken token = new PasswordResetToken();
        token.id = UUID.randomUUID();
        token.userId = userId;
        token.tokenHash = tokenHash;
        token.createdAt = now;
        token.expiresAt = now.plus(ttl);
        return token;
    }

    /** Còn dùng được: chưa dùng, chưa bị thư mới hơn thay thế. Hết hạn kiểm tra riêng để báo đúng lý do. */
    public boolean isOpen() {
        return usedAt == null && invalidatedAt == null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
