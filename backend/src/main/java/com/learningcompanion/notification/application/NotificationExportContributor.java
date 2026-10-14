package com.learningcompanion.notification.application;

import com.learningcompanion.notification.domain.Notification;
import com.learningcompanion.notification.domain.NotificationRepository;
import com.learningcompanion.shared.export.DataExportContributor;
import com.learningcompanion.shared.export.ExportFormat;
import com.learningcompanion.shared.export.ExportWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Phần "Thông báo" của gói xuất (NCL-01-CN-008): các thông báo trong chín mươi ngày gần nhất. */
@Component
public class NotificationExportContributor implements DataExportContributor {

    private final NotificationRepository notifications;

    public NotificationExportContributor(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Override
    public String section() {
        return "notifications";
    }

    @Override
    public String title() {
        return "Thông báo";
    }

    @Override
    public List<String> describe() {
        return List.of("notifications.json: thông báo trong 90 ngày gần nhất, gồm loại, thời điểm, đối tượng liên "
                + "quan và lúc bạn đã đọc.");
    }

    @Override
    public List<ExportFormat> formats() {
        return List.of(ExportFormat.JSON);
    }

    @Override
    public int order() {
        return 26;
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(UUID ownerId, ExportWriter writer) {
        List<Map<String, Object>> rows = notifications.findAllByOwnerIdOrderByCreatedAtAsc(ownerId).stream()
                .map(NotificationExportContributor::row)
                .toList();
        writer.json("notifications.json", rows);
    }

    private static Map<String, Object> row(Notification notification) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", notification.getType().name());
        row.put("details", notification.getParams());
        row.put("target", notification.getTargetType().name());
        row.put("targetId", notification.getTargetId());
        row.put("createdAt", notification.getCreatedAt());
        row.put("readAt", notification.getReadAt());
        return row;
    }
}
