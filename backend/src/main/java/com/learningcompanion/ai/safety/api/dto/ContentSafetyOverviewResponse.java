package com.learningcompanion.ai.safety.api.dto;

import com.learningcompanion.ai.safety.domain.InjectionSignal;
import com.learningcompanion.ai.safety.domain.ModerationCategory;
import com.learningcompanion.ai.safety.domain.SafetyEvent;
import com.learningcompanion.ai.safety.domain.SafetyLevel;
import com.learningcompanion.shared.audit.AuditObjectType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Trang an toàn nội dung của quản trị viên: ngưỡng lọc từng nhóm và các lần bộ lọc can thiệp gần nhất. Sự kiện chỉ có
 * loại vi phạm và mã đối tượng, không có nội dung.
 */
public record ContentSafetyOverviewResponse(List<Level> levels, List<Event> events) {

    public record Actor(UUID id, String displayName) {
    }

    /** {@code updatedBy} trống khi vẫn là ngưỡng mặc định lúc cài đặt. */
    public record Level(ModerationCategory category, SafetyLevel level, SafetyLevel defaultLevel, Instant updatedAt,
                        Actor updatedBy) {
    }

    public record Event(UUID id, Instant occurredAt, SafetyEvent.Kind kind, ModerationCategory category,
                        InjectionSignal signal, String feature, AuditObjectType objectType, UUID objectId) {

        public static Event of(SafetyEvent event) {
            return new Event(event.getId(), event.getOccurredAt(), event.getKind(), event.getCategory(),
                    event.getSignal(), event.getFeature(), event.getObjectType(), event.getObjectId());
        }
    }
}
