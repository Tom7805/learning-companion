package com.learningcompanion.workspace.domain;

import com.learningcompanion.library.domain.TagColor;
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

/**
 * Một không gian học của người học (NCL-06-CN-001): nơi làm việc riêng cho một điều cần học, gắn với nhóm tài liệu chọn
 * từ kho. Không gian học không chứa tài liệu mà chỉ liên kết tới ({@link WorkspaceDocument}, QTN-17). Xóa chỉ đánh dấu
 * vào thùng rác kèm mã đợt xóa (QTN-18).
 */
@Entity
@Table(name = "workspaces")
public class Workspace implements OwnedEntity {

    public static final int NAME_MAX_LENGTH = 100;
    public static final int DESCRIPTION_MAX_LENGTH = 500;

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private WorkspaceIcon icon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TagColor color;

    @Column(nullable = false, length = DESCRIPTION_MAX_LENGTH)
    private String description;

    @Column(name = "default_folder_id")
    private UUID defaultFolderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_opened_at")
    private Instant lastOpenedAt;

    @Column(name = "trashed_at")
    private Instant trashedAt;

    @Column(name = "trash_batch_id")
    private UUID trashBatchId;

    @Version
    private long version;

    protected Workspace() {
    }

    public static Workspace create(UUID ownerId, String name, WorkspaceIcon icon, TagColor color, String description,
                                   Instant now) {
        Workspace workspace = new Workspace();
        workspace.id = UUID.randomUUID();
        workspace.ownerId = ownerId;
        workspace.name = name;
        workspace.icon = icon;
        workspace.color = color;
        workspace.description = description;
        workspace.createdAt = now;
        workspace.updatedAt = now;
        return workspace;
    }

    public void edit(String newName, WorkspaceIcon newIcon, TagColor newColor, String newDescription, Instant now) {
        this.name = newName;
        this.icon = newIcon;
        this.color = newColor;
        this.description = newDescription;
        this.updatedAt = now;
    }

    public void useDefaultFolder(UUID folderId) {
        this.defaultFolderId = folderId;
    }

    public void markOpened(Instant now) {
        this.lastOpenedAt = now;
    }

    /** Gắn hay gỡ tài liệu là một lần thay đổi không gian học. */
    public void touch(Instant now) {
        this.updatedAt = now;
    }

    public void trash(UUID batch, Instant now) {
        this.trashedAt = now;
        this.trashBatchId = batch;
    }

    public boolean isTrashed() {
        return trashedAt != null;
    }

    /** Thời điểm dùng gần nhất, để danh sách đưa không gian học vừa mở hay vừa tạo lên đầu. */
    public Instant lastUsedAt() {
        return lastOpenedAt != null && lastOpenedAt.isAfter(createdAt) ? lastOpenedAt : createdAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public WorkspaceIcon getIcon() {
        return icon;
    }

    public TagColor getColor() {
        return color;
    }

    public String getDescription() {
        return description;
    }

    public UUID getDefaultFolderId() {
        return defaultFolderId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getLastOpenedAt() {
        return lastOpenedAt;
    }

    public Instant getTrashedAt() {
        return trashedAt;
    }

    public UUID getTrashBatchId() {
        return trashBatchId;
    }
}
