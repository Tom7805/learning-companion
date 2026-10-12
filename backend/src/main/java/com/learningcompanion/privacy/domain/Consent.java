package com.learningcompanion.privacy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Trạng thái đồng ý hiện tại của một mục đích. */
@Entity
@Table(name = "consents")
public class Consent {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConsentPurpose purpose;

    @Column(nullable = false)
    private boolean granted;

    @Column(name = "document_version", nullable = false)
    private String documentVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Consent() {
    }

    public static Consent grant(UUID userId, ConsentPurpose purpose, String documentVersion, Instant now) {
        return record(userId, purpose, true, documentVersion, now);
    }

    public static Consent record(UUID userId, ConsentPurpose purpose, boolean granted, String documentVersion,
                                 Instant now) {
        Consent consent = new Consent();
        consent.id = UUID.randomUUID();
        consent.userId = userId;
        consent.purpose = purpose;
        consent.granted = granted;
        consent.documentVersion = documentVersion;
        consent.updatedAt = now;
        return consent;
    }

    /** Đổi trạng thái đồng ý; trả về false nếu không có gì thay đổi để khỏi ghi lịch sử thừa. */
    public boolean change(boolean grantedNow, String version, Instant now) {
        if (granted == grantedNow && documentVersion.equals(version)) {
            return false;
        }
        granted = grantedNow;
        documentVersion = version;
        updatedAt = now;
        return true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public ConsentPurpose getPurpose() {
        return purpose;
    }

    public boolean isGranted() {
        return granted;
    }

    public String getDocumentVersion() {
        return documentVersion;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
