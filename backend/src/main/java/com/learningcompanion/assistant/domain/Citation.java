package com.learningcompanion.assistant.domain;

import com.learningcompanion.library.domain.ChunkLocation;
import com.learningcompanion.shared.ownership.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Một trích dẫn trong câu trả lời (QTN-21): số trích dẫn, tài liệu, vị trí trong tài liệu gốc (QTN-20) và đoạn gốc.
 * Tiêu đề và vị trí được chụp lại lúc trả lời nên trích dẫn vẫn đọc được khi tài liệu sau đó bị xóa.
 */
@Entity
@Table(name = "assistant_citations")
public class Citation implements OwnedEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Column(nullable = false, updatable = false)
    private int number;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "chunk_id")
    private UUID chunkId;

    @Column(name = "document_title", nullable = false, length = 200)
    private String documentTitle;

    @Embedded
    private ChunkLocation location;

    /** Thứ tự đoạn trong trang hoặc trang trình chiếu, bắt đầu từ 1, như khung xem tài liệu dùng. */
    @Column(nullable = false)
    private int passage;

    @Column(nullable = false, columnDefinition = "text")
    private String excerpt;

    protected Citation() {
    }

    public static Citation of(UUID ownerId, UUID messageId, int number, UUID documentId, UUID chunkId,
                              String documentTitle, ChunkLocation location, int passage, String excerpt) {
        Citation citation = new Citation();
        citation.id = UUID.randomUUID();
        citation.ownerId = ownerId;
        citation.messageId = messageId;
        citation.number = number;
        citation.documentId = documentId;
        citation.chunkId = chunkId;
        citation.documentTitle = documentTitle;
        citation.location = location;
        citation.passage = passage;
        citation.excerpt = excerpt;
        return citation;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public int getNumber() {
        return number;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public UUID getChunkId() {
        return chunkId;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public ChunkLocation getLocation() {
        return location;
    }

    public int getPassage() {
        return passage;
    }

    public String getExcerpt() {
        return excerpt;
    }
}
