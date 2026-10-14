package com.learningcompanion.notification.api.dto;

import com.learningcompanion.notification.domain.Notification;
import com.learningcompanion.notification.domain.NotificationTargetType;
import com.learningcompanion.notification.domain.NotificationType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Một thông báo như giao diện hiển thị; tiêu đề được dựng ở giao diện từ loại và tham số. */
public record NotificationResponse(UUID id, NotificationType type, Map<String, Object> params,
                                   NotificationTargetType targetType, Instant createdAt, Instant readAt) {

    public static NotificationResponse of(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getParams(),
                notification.getTargetType(), notification.getCreatedAt(), notification.getReadAt());
    }
}
