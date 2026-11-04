package com.learningcompanion.library.domain;

import com.learningcompanion.shared.ownership.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Một thư mục trong kho tài liệu của người học (NCL-05-CN-001). Thư mục lồng nhau không giới hạn số cấp; thư mục gốc
 * có {@code parentId} rỗng. Xóa chỉ đánh dấu vào thùng rác kèm mã đợt xóa (QTN-18).
 */
@Entity
@Table(name = "library_folders")
public class Folder implements OwnedEntity {

    public static final int NAME_MAX_LENGTH = 120;

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "trashed_at")
    private Instant trashedAt;

    @Column(name = "trash_batch_id")
    private UUID trashBatchId;

    @Version
    private long version;

    protected Folder() {
    }

    public static Folder create(UUID ownerId, UUID parentId, String name, Instant now) {
        Folder folder = new Folder();
        folder.id = UUID.randomUUID();
        folder.ownerId = ownerId;
        folder.parentId = parentId;
        folder.name = name;
        folder.createdAt = now;
        folder.updatedAt = now;
        return folder;
    }

    public void rename(String newName, Instant now) {
        this.name = newName;
        this.updatedAt = now;
    }

    public void moveTo(UUID newParentId, Instant now) {
        this.parentId = newParentId;
        this.updatedAt = now;
    }

    public boolean isTrashed() {
        return trashedAt != null;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getTrashedAt() {
        return trashedAt;
    }

    public UUID getTrashBatchId() {
        return trashBatchId;
    }
}
