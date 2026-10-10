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
        Consent consent = new Consent();
        consent.id = UUID.randomUUID();
        consent.userId = userId;
        consent.purpose = purpose;
        consent.granted = true;
        consent.documentVersion = documentVersion;
        consent.updatedAt = now;
        return consent;
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
}
