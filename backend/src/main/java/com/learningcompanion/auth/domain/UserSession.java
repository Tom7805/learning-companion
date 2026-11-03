package com.learningcompanion.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Phiên đăng nhập trên một thiết bị. Không ghi nhớ thiết bị: hết hạn sau một khoảng không thao tác.
 * Ghi nhớ thiết bị: có hiệu lực cố định từ lúc đăng nhập (30 ngày), thao tác không kéo dài thêm.
 */
@Entity
@Table(name = "user_sessions")
public class UserSession {

    public enum State { ACTIVE, EXPIRED, REVOKED }

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

    @Column(name = "device_id_hash", length = 64)
    private String deviceIdHash;

    @Column(name = "browser")
    private String browser;

    @Column(name = "operating_system")
    private String operatingSystem;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type")
    private DeviceInfo.DeviceType deviceType;

    @Column(name = "location")
    private String location;

    @Column(name = "local_network", nullable = false)
    private boolean localNetwork;

    @Column(name = "revoke_link_hash", length = 64)
    private String revokeLinkHash;

    /** Hạn cứng của phiên quản trị (tám giờ kể từ lúc đăng nhập); thao tác không kéo dài quá mốc này. */
    @Column(name = "max_expires_at")
    private Instant maxExpiresAt;

    /** Phiên đã qua bước mã xác thực thứ hai; chỉ phiên như vậy mới mang quyền quản trị (NCL-03-CN-002). */
    @Column(name = "two_factor_verified", nullable = false)
    private boolean twoFactorVerified;

    protected UserSession() {
    }

    public static UserSession start(UUID userId, String tokenHash, Instant now, boolean rememberDevice,
                                    Duration lifetime, String deviceIdHash, DeviceInfo device,
                                    String userAgent, String ipAddress) {
        UserSession session = new UserSession();
        session.id = UUID.randomUUID();
        session.userId = userId;
        session.tokenHash = tokenHash;
        session.rememberDevice = rememberDevice;
        session.createdAt = now;
        session.lastActiveAt = now;
        session.expiresAt = now.plus(lifetime);
        session.deviceIdHash = deviceIdHash;
        session.browser = device.browser();
        session.operatingSystem = device.operatingSystem();
        session.deviceType = device.deviceType();
        session.location = device.location();
        session.localNetwork = device.localNetwork();
        session.userAgent = userAgent;
        session.ipAddress = ipAddress;
        return session;
    }

    /**
     * Phiên quản trị sau khi qua bước hai: không ghi nhớ thiết bị, hết hạn khi không thao tác một khoảng
     * {@code idleTimeout} hoặc khi tới hạn cứng {@code maxLifetime} kể từ lúc đăng nhập.
     */
    public static UserSession startTwoFactor(UUID userId, String tokenHash, Instant now, Duration idleTimeout,
                                             Duration maxLifetime, String deviceIdHash, DeviceInfo device,
                                             String userAgent, String ipAddress) {
        UserSession session = start(userId, tokenHash, now, false, idleTimeout, deviceIdHash, device, userAgent,
                ipAddress);
        session.maxExpiresAt = now.plus(maxLifetime);
        if (session.expiresAt.isAfter(session.maxExpiresAt)) {
            session.expiresAt = session.maxExpiresAt;
        }
        session.twoFactorVerified = true;
        return session;
    }

    public State state(Instant now) {
        if (revokedAt != null) {
            return State.REVOKED;
        }
        return now.isBefore(expiresAt) ? State.ACTIVE : State.EXPIRED;
    }

    public boolean isActive(Instant now) {
        return state(now) == State.ACTIVE;
    }

    /**
     * Ghi nhận thao tác, tối đa mỗi phút một lần xuống cơ sở dữ liệu. Phiên không ghi nhớ được kéo dài
     * thêm một khoảng không thao tác; phiên ghi nhớ giữ nguyên hạn 30 ngày.
     */
    public void touch(Instant now, Duration idleTimeout) {
        if (Duration.between(lastActiveAt, now).compareTo(Duration.ofMinutes(1)) < 0) {
            return;
        }
        lastActiveAt = now;
        if (!rememberDevice) {
            expiresAt = now.plus(idleTimeout);
            if (maxExpiresAt != null && expiresAt.isAfter(maxExpiresAt)) {
                expiresAt = maxExpiresAt;
            }
        }
    }

    public void revoke(Instant now, String reason) {
        if (revokedAt == null) {
            revokedAt = now;
            revokeReason = reason;
        }
    }

    public void attachRevokeLink(String linkHash) {
        this.revokeLinkHash = linkHash;
    }

    public DeviceInfo device() {
        return new DeviceInfo(browser, operatingSystem, deviceType, location, localNetwork);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public boolean isRememberDevice() {
        return rememberDevice;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getMaxExpiresAt() {
        return maxExpiresAt;
    }

    public boolean isTwoFactorVerified() {
        return twoFactorVerified;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getRevokeReason() {
        return revokeReason;
    }
}
