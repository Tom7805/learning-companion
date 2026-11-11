package com.learningcompanion.workspace.domain;

import com.learningcompanion.shared.ownership.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Một chủ đề trong dàn ý của không gian học (NCL-06-CN-002). Dàn ý là cây tối đa {@link #MAX_DEPTH} cấp; thứ tự giữa
 * các chủ đề cùng cha theo {@code position}. Chủ đề do trợ lý đề xuất mang {@link Origin#AI}; người học sửa tay thì
 * {@code edited} bật và lần đề xuất lại giữ nguyên chủ đề đó (TC-03).
 */
@Entity
@Table(name = "workspace_topics")
public class Topic implements OwnedEntity {

    public static final int MAX_DEPTH = 3;
    public static final int TITLE_MAX_LENGTH = 200;
    public static final int GOAL_MAX_LENGTH = 500;

    /** Ai tạo ra chủ đề: người học tự thêm, hay trợ lý đề xuất. */
    public enum Origin { MANUAL, AI }

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(nullable = false, length = GOAL_MAX_LENGTH)
    private String goal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8, updatable = false)
    private Origin origin;

    @Column(nullable = false)
    private boolean edited;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Topic() {
    }

    public static Topic create(UUID ownerId, UUID workspaceId, UUID parentId, int position, String title, String goal,
                               Origin origin, Instant now) {
        Topic topic = new Topic();
        topic.id = UUID.randomUUID();
        topic.ownerId = ownerId;
        topic.workspaceId = workspaceId;
        topic.parentId = parentId;
        topic.position = position;
        topic.title = title;
        topic.goal = goal == null ? "" : goal;
        topic.origin = origin;
        topic.createdAt = now;
        topic.updatedAt = now;
        return topic;
    }

    /** Người học sửa tên hay mục tiêu học: chủ đề do trợ lý đề xuất giữ nhãn và ghi thêm "đã chỉnh sửa". */
    public void rename(String newTitle, String newGoal, Instant now) {
        if (newTitle != null) {
            title = newTitle;
        }
        if (newGoal != null) {
            goal = newGoal;
        }
        edited = true;
        updatedAt = now;
    }

    public void moveTo(UUID newParentId, int newPosition, Instant now) {
        parentId = newParentId;
        position = newPosition;
        updatedAt = now;
    }

    public void place(int newPosition) {
        position = newPosition;
    }

    public void markEdited(Instant now) {
        edited = true;
        updatedAt = now;
    }

    /** Chủ đề người học đã đụng tới (tự thêm hay sửa tay): lần đề xuất lại không được xóa. */
    public boolean ownedByLearner() {
        return origin == Origin.MANUAL || edited;
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

    public UUID getParentId() {
        return parentId;
    }

    public int getPosition() {
        return position;
    }

    public String getTitle() {
        return title;
    }

    public String getGoal() {
        return goal;
    }

    public Origin getOrigin() {
        return origin;
    }

    public boolean isEdited() {
        return edited;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
