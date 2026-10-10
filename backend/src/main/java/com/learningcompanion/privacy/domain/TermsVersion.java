package com.learningcompanion.privacy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "terms_versions")
public class TermsVersion {

    public enum DocumentType { TERMS_OF_SERVICE, PRIVACY_POLICY }

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private DocumentType documentType;

    @Column(nullable = false)
    private String version;

    @Column(nullable = false)
    private String summary;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    protected TermsVersion() {
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public String getVersion() {
        return version;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getEffectiveAt() {
        return effectiveAt;
    }
}
