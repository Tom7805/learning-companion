package com.learningcompanion.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Tài khoản của nhà cung cấp định danh bên ngoài đã liên kết với một tài khoản người học. */
@Entity
@Table(name = "external_identities")
public class ExternalIdentity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private IdentityProvider provider;

    /** Mã định danh bất biến do nhà cung cấp cấp (claim "sub"), không phải địa chỉ thư. */
    @Column(nullable = false, updatable = false)
    private String subject;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected ExternalIdentity() {
    }

    public static ExternalIdentity link(UUID userId, IdentityProvider provider, String subject, String email,
                                        Instant now) {
        ExternalIdentity identity = new ExternalIdentity();
        identity.id = UUID.randomUUID();
        identity.userId = userId;
        identity.provider = provider;
        identity.subject = subject;
        identity.email = email;
        identity.linkedAt = now;
        identity.lastUsedAt = now;
        return identity;
    }

    public void markUsed(String currentEmail, Instant now) {
        email = currentEmail;
        lastUsedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
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

    public Instant getLinkedAt() {
        return linkedAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
}
