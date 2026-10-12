package com.learningcompanion.privacy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    /** Điểm chính viết bằng lời dễ hiểu. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> highlights = List.of();

    /** Điểm thay đổi so với phiên bản trước, nêu trong hộp thoại đồng ý lại. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> changes = List.of();

    protected TermsVersion() {
    }

    public static TermsVersion publish(DocumentType type, String version, String summary, List<String> highlights,
                                       List<String> changes, Instant effectiveAt) {
        TermsVersion terms = new TermsVersion();
        terms.id = UUID.randomUUID();
        terms.documentType = type;
        terms.version = version;
        terms.summary = summary;
        terms.highlights = List.copyOf(highlights);
        terms.changes = List.copyOf(changes);
        terms.effectiveAt = effectiveAt;
        return terms;
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

    public List<String> getHighlights() {
        return highlights;
    }

    public List<String> getChanges() {
        return changes;
    }
}
