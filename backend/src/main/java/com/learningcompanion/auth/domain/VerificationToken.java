package com.learningcompanion.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Đường dẫn xác thực thư điện tử dùng một lần, chỉ lưu băm của chuỗi bí mật. */
@Entity
@Table(name = "verification_tokens")
public class VerificationToken {

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

    protected VerificationToken() {
    }

    public static VerificationToken issue(UUID userId, String tokenHash, Instant now, Duration ttl) {
        VerificationToken token = new VerificationToken();
        token.id = UUID.randomUUID();
        token.userId = userId;
        token.tokenHash = tokenHash;
        token.createdAt = now;
        token.expiresAt = now.plus(ttl);
        return token;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isInvalidated() {
        return invalidatedAt != null;
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
