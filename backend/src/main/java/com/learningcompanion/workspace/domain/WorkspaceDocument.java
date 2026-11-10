package com.learningcompanion.workspace.domain;

import com.learningcompanion.shared.ownership.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Liên kết một tài liệu trong kho với một không gian học (QTN-17). Tài liệu chỉ lưu và xử lý một lần; gắn vào bao nhiêu
 * không gian học cũng chỉ thêm dòng liên kết này, gỡ ra chỉ xóa dòng này.
 */
@Entity
@Table(name = "workspace_documents")
public class WorkspaceDocument implements OwnedEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "attached_at", nullable = false, updatable = false)
    private Instant attachedAt;

    protected WorkspaceDocument() {
    }

    public static WorkspaceDocument link(UUID ownerId, UUID workspaceId, UUID documentId, Instant now) {
        WorkspaceDocument link = new WorkspaceDocument();
        link.id = UUID.randomUUID();
        link.ownerId = ownerId;
        link.workspaceId = workspaceId;
        link.documentId = documentId;
        link.attachedAt = now;
        return link;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public Instant getAttachedAt() {
        return attachedAt;
    }
}
