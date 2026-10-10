package com.learningcompanion.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_sessions")
public class UserSession {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "remember_device", nullable = false)
    private boolean rememberDevice;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason")
    private String revokeReason;

    protected UserSession() {
    }

    /** Phiên không ghi nhớ thiết bị: hết hạn sau một khoảng không thao tác. */
    public static UserSession startIdleSession(UUID userId, String tokenHash, Instant now, Duration idleTimeout,
                                               String userAgent, String ipAddress) {
        UserSession session = new UserSession();
        session.id = UUID.randomUUID();
        session.userId = userId;
        session.tokenHash = tokenHash;
        session.rememberDevice = false;
        session.createdAt = now;
        session.lastActiveAt = now;
        session.expiresAt = now.plus(idleTimeout);
        session.userAgent = userAgent;
        session.ipAddress = ipAddress;
        return session;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    /** Gia hạn khi có thao tác, chỉ ghi xuống cơ sở dữ liệu tối đa mỗi phút một lần. */
    public boolean touch(Instant now, Duration idleTimeout) {
        if (Duration.between(lastActiveAt, now).compareTo(Duration.ofMinutes(1)) < 0) {
            return false;
        }
        lastActiveAt = now;
        expiresAt = now.plus(idleTimeout);
        return true;
    }

    public void revoke(Instant now, String reason) {
        if (revokedAt == null) {
            revokedAt = now;
            revokeReason = reason;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
