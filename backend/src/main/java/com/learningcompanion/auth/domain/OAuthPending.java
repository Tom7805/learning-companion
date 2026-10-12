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
 * Bước còn dang dở sau khi nhà cung cấp đã xác nhận người dùng: người mới phải đồng ý điều khoản (SIGNUP),
 * địa chỉ thư đã có tài khoản thì phải nhập mật khẩu để liên kết (LINK). Chỉ lưu băm của mã tiếp tục.
 */
@Entity
@Table(name = "oauth_pending")
public class OAuthPending {

    public enum Kind { SIGNUP, LINK }

    @Id
    private UUID id;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdentityProvider provider;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "display_name", length = 100)
    private String displayName;

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

    protected OAuthPending() {
    }

    public static OAuthPending create(String tokenHash, Kind kind, IdentityProvider provider, String subject,
                                      String email, String displayName, boolean rememberDevice, String returnTo,
                                      Instant now, Duration ttl) {
        OAuthPending pending = new OAuthPending();
        pending.id = UUID.randomUUID();
        pending.tokenHash = tokenHash;
        pending.kind = kind;
        pending.provider = provider;
        pending.subject = subject;
        pending.email = email;
        pending.displayName = displayName;
        pending.rememberDevice = rememberDevice;
        pending.returnTo = returnTo;
        pending.createdAt = now;
        pending.expiresAt = now.plus(ttl);
        return pending;
    }

    public boolean isOpen(Instant now) {
        return consumedAt == null && now.isBefore(expiresAt);
    }

    public void consume(Instant now) {
        consumedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Kind getKind() {
        return kind;
    }

    public IdentityProvider getProvider() {
        return provider;
    }

    public String getSubject() {
        return subject;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isRememberDevice() {
        return rememberDevice;
    }

    public String getReturnTo() {
        return returnTo;
    }
}
