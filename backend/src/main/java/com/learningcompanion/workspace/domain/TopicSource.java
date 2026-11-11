package com.learningcompanion.workspace.domain;

import com.learningcompanion.library.domain.ChunkLocation;
import com.learningcompanion.shared.ownership.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Một đoạn nguồn mà chủ đề trích tới (NCL-06-CN-002, QTN-21): chụp lại tên tài liệu, vị trí, thứ tự đoạn theo cách
 * đánh số của khung xem và đoạn gốc, để vẫn hiển thị được khi tài liệu đã bị xóa ({@code documentId} thành null).
 */
@Entity
@Table(name = "workspace_topic_sources")
public class TopicSource implements OwnedEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "topic_id", nullable = false)
    private UUID topicId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "document_title", nullable = false, length = 300)
    private String documentTitle;

    @Embedded
    private ChunkLocation location;

    @Column(nullable = false)
    private int passage;

    @Column(nullable = false, columnDefinition = "text")
    private String excerpt;

    protected TopicSource() {
    }

    public static TopicSource of(UUID ownerId, UUID topicId, UUID documentId, String documentTitle,
                                 ChunkLocation location, int passage, String excerpt) {
        TopicSource source = new TopicSource();
        source.id = UUID.randomUUID();
        source.ownerId = ownerId;
        source.topicId = topicId;
        source.documentId = documentId;
        source.documentTitle = documentTitle.length() > 300 ? documentTitle.substring(0, 300) : documentTitle;
        source.location = location;
        source.passage = passage;
        source.excerpt = excerpt;
        return source;
    }

    /** Bản sao gắn vào chủ đề khác (khi gộp chủ đề). */
    public TopicSource copyTo(UUID otherTopicId) {
        return of(ownerId, otherTopicId, documentId, documentTitle, location, passage, excerpt);
    }

    /** Cùng một đoạn của cùng một tài liệu. */
    public boolean samePassage(TopicSource other) {
        return java.util.Objects.equals(documentId, other.documentId) && location.equals(other.location)
                && passage == other.passage;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getTopicId() {
        return topicId;
    }

    public UUID getDocumentId() {
        return documentId;
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
