package com.learningcompanion.library.domain;

import com.learningcompanion.shared.ownership.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Tài liệu học tập của một người học; tệp gốc nằm mã hóa trong kho tệp, ở đây chỉ giữ khóa của nó. */
@Entity
@Table(name = "documents")
public class Document implements OwnedEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 20)
    private DocumentType type;

    @Column(name = "content_text", nullable = false)
    private String contentText;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false, length = 127)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 100)
    private String storageKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Document() {
    }

    public static Document create(UUID ownerId, String title, DocumentType type, String contentText,
                                  String originalFilename, String mimeType, long sizeBytes, String storageKey,
                                  Instant now) {
        Document document = new Document();
        document.id = UUID.randomUUID();
        document.ownerId = ownerId;
        document.title = title;
        document.type = type;
        document.contentText = contentText;
        document.originalFilename = originalFilename;
        document.mimeType = mimeType;
        document.sizeBytes = sizeBytes;
        document.storageKey = storageKey;
        document.createdAt = now;
        document.updatedAt = now;
        return document;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getOwnerId() {
        return ownerId;
    }

    public String getTitle() {
        return title;
    }

    public DocumentType getType() {
        return type;
    }

    public String getContentText() {
        return contentText;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
