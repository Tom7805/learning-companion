package com.learningcompanion.privacy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Một lần đồng ý hoặc rút lại, chỉ ghi thêm để tra lại được về sau. */
@Entity
@Immutable
@Table(name = "consent_history")
public class ConsentHistory {

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

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected ConsentHistory() {
    }

    public static ConsentHistory of(Consent consent, Instant now) {
        ConsentHistory entry = new ConsentHistory();
        entry.id = UUID.randomUUID();
        entry.userId = consent.getUserId();
        entry.purpose = consent.getPurpose();
        entry.granted = consent.isGranted();
        entry.documentVersion = consent.getDocumentVersion();
        entry.recordedAt = now;
        return entry;
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

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
