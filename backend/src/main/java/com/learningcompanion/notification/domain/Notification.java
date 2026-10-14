package com.learningcompanion.notification.domain;

import com.learningcompanion.shared.ownership.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Một thông báo trong ứng dụng của một người học (NCL-02-CN-002). Được tạo qua
 * {@code NotificationService.notify}, chỉ đổi được trạng thái đã đọc.
 */
@Entity
@Table(name = "notifications")
public class Notification implements OwnedEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 64)
    private NotificationType type;

    /** Dữ liệu để giao diện dựng tiêu đề, ví dụ tên thiết bị; không bao giờ chứa nội dung học tập. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> params = new LinkedHashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false, length = 64)
    private NotificationTargetType targetType;

    @Column(name = "target_id", updatable = false)
    private UUID targetId;

    @Column(name = "dedup_key", nullable = false, updatable = false, length = 200)
    private String dedupKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    /** Đánh dấu đã đọc; trả về false nếu đã đọc từ trước. */
    public boolean markRead(Instant now) {
        if (readAt != null) {
            return false;
        }
        readAt = now;
        return true;
    }

    public boolean isRead() {
        return readAt != null;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getOwnerId() {
        return ownerId;
    }

    public NotificationType getType() {
        return type;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public NotificationTargetType getTargetType() {
        return targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
