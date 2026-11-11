package com.learningcompanion.assistant.domain;

import com.learningcompanion.shared.ownership.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Một hội thoại với trợ lý (NCL-07-CN-001): thuộc một không gian học ({@code workspaceId}) hoặc là hội thoại riêng với
 * tài liệu chọn từ kho. Tên lấy từ câu hỏi đầu tiên.
 */
@Entity
@Table(name = "assistant_conversations")
public class Conversation implements OwnedEntity {

    public static final int TITLE_MAX_LENGTH = 120;

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "trashed_at")
    private Instant trashedAt;

    @Column(name = "pinned_at")
    private Instant pinnedAt;

    @Version
    private long version;

    protected Conversation() {
    }

    public static Conversation start(UUID ownerId, UUID workspaceId, String firstQuestion, Instant now) {
        Conversation conversation = new Conversation();
        conversation.id = UUID.randomUUID();
        conversation.ownerId = ownerId;
        conversation.workspaceId = workspaceId;
        conversation.title = titleOf(firstQuestion);
        conversation.createdAt = now;
        conversation.updatedAt = now;
        return conversation;
    }

    /** Tên hội thoại: câu hỏi đầu tiên đã gọn khoảng trắng, cắt ở ranh giới từ nếu quá dài. */
    public static String titleOf(String question) {
        String clean = question == null ? "" : question.strip().replaceAll("\\s+", " ");
        if (clean.length() <= TITLE_MAX_LENGTH) {
            return clean.isEmpty() ? "Hội thoại mới" : clean;
        }
        String cut = clean.substring(0, TITLE_MAX_LENGTH - 1);
        int space = cut.lastIndexOf(' ');
        return (space > TITLE_MAX_LENGTH / 2 ? cut.substring(0, space) : cut).strip() + "…";
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    /** Đổi tên (NCL-07-CN-003); tên trống thì giữ tên cũ, quá dài thì cắt như tên tự đặt. */
    public void rename(String newTitle) {
        if (newTitle != null && !newTitle.isBlank()) {
            this.title = titleOf(newTitle);
        }
    }

    /** Ghim lên đầu danh sách hay bỏ ghim; thời điểm ghim xếp các hội thoại đã ghim với nhau, mới ghim trước. */
    public void pin(boolean pinned, Instant now) {
        if (pinned && pinnedAt == null) {
            this.pinnedAt = now;
        } else if (!pinned) {
            this.pinnedAt = null;
        }
    }

    /**
     * Chuyển hội thoại vào một không gian học (hay ra thành hội thoại riêng khi {@code null}): từ lượt hỏi sau, phạm vi
     * tài liệu theo không gian học mới (TC-04).
     */
    public void moveTo(UUID newWorkspaceId) {
        this.workspaceId = newWorkspaceId;
    }

    /** Bỏ vào thùng rác; không còn hiện trong danh sách và mở lại trả 404. */
    public void trash(Instant now) {
        this.trashedAt = now;
        this.pinnedAt = null;
    }

    public boolean isPinned() {
        return pinnedAt != null;
    }

    public Instant getPinnedAt() {
        return pinnedAt;
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

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getTitle() {
        return title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
