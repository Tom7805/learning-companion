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
 * Một lượt ủy quyền qua nhà cung cấp định danh. Mã chống giả mạo (state) và mã gắn trình duyệt chỉ lưu băm;
 * mã xác minh PKCE giữ nguyên vì phải gửi cho nhà cung cấp khi đổi mã ủy quyền, và chỉ sống mười phút.
 */
@Entity
@Table(name = "oauth_flows")
public class OAuthFlow {

    public enum Mode { LOGIN, LINK }

    @Id
    private UUID id;

    @Column(name = "state_hash", nullable = false, length = 64)
    private String stateHash;

    @Column(name = "browser_hash", nullable = false, length = 64)
    private String browserHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdentityProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Mode mode;

    /** Người học đang đăng nhập khi liên kết từ phần cài đặt. */
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "code_verifier", nullable = false, length = 128)
    private String codeVerifier;

    @Column(name = "remember_device", nullable = false)
    private boolean rememberDevice;

    @Column(name = "return_to", length = 200)
    private String returnTo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected OAuthFlow() {
    }

    public static OAuthFlow start(String stateHash, String browserHash, IdentityProvider provider, Mode mode,
                                  UUID userId, String codeVerifier, boolean rememberDevice, String returnTo,
                                  Instant now, Duration ttl) {
        OAuthFlow flow = new OAuthFlow();
        flow.id = UUID.randomUUID();
        flow.stateHash = stateHash;
        flow.browserHash = browserHash;
        flow.provider = provider;
        flow.mode = mode;
        flow.userId = userId;
        flow.codeVerifier = codeVerifier;
        flow.rememberDevice = rememberDevice;
        flow.returnTo = returnTo;
        flow.createdAt = now;
        flow.expiresAt = now.plus(ttl);
        return flow;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public String getBrowserHash() {
        return browserHash;
    }

    public IdentityProvider getProvider() {
        return provider;
    }

    public Mode getMode() {
        return mode;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCodeVerifier() {
        return codeVerifier;
    }

    public boolean isRememberDevice() {
        return rememberDevice;
    }

    public String getReturnTo() {
        return returnTo;
    }
}
