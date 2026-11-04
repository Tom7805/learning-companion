package com.learningcompanion.library.domain;

import com.learningcompanion.shared.ownership.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Một đoạn của tài liệu đã xử lý (NCL-05-CN-003): văn bản, vị trí trong tài liệu gốc (QTN-20), đề mục gần nhất và
 * biểu diễn ngữ nghĩa để tìm theo nghĩa. Đoạn thuộc chủ sở hữu của tài liệu và chỉ được đọc kèm mã chủ sở hữu.
 */
@Entity
@Table(name = "document_chunks")
public class DocumentChunk implements OwnedEntity {

    /** Văn bản lấy từ lớp chữ của tệp hay nhận dạng từ hình. */
    public enum Source { TEXT, OCR }

    @Id
    private UUID id;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, updatable = false)
    private int ordinal;

    @Embedded
    private ChunkLocation location;

    @Column(length = 300, updatable = false)
    private String heading;

    @Column(nullable = false, updatable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Source source;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, updatable = false, columnDefinition = "real[]")
    private float[] embedding;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DocumentChunk() {
    }

    public static DocumentChunk create(Document document, int ordinal, ChunkLocation location, String heading,
                                       String content, Source source, float[] embedding, Instant now) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.id = UUID.randomUUID();
        chunk.documentId = document.getId();
        chunk.ownerId = document.getOwnerId();
        chunk.ordinal = ordinal;
        chunk.location = location;
        chunk.heading = heading;
        chunk.content = content;
        chunk.source = source;
        chunk.embedding = embedding.clone();
        chunk.createdAt = now;
        return chunk;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public ChunkLocation getLocation() {
        return location;
    }

    public String getHeading() {
        return heading;
    }

    public String getContent() {
        return content;
    }

    public Source getSource() {
        return source;
    }

    public float[] getEmbedding() {
        return embedding.clone();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
