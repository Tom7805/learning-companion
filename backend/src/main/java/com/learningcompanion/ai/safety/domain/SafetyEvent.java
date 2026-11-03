package com.learningcompanion.ai.safety.domain;

import com.learningcompanion.shared.audit.AuditObjectType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Một lần bộ lọc can thiệp (NCL-04-CN-004). Chỉ có loại vi phạm và mã đối tượng; nội dung câu hỏi, câu trả lời hay
 * đoạn nguồn không bao giờ được lưu ở đây.
 */
@Entity
@Table(name = "ai_safety_events")
public class SafetyEvent {

    public enum Kind { INPUT_BLOCKED, OUTPUT_BLOCKED, SELF_HARM_SUPPORT, INJECTION_FLAGGED }

    @Id
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(nullable = false, length = 60)
    private String feature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ModerationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private InjectionSignal signal;

    @Enumerated(EnumType.STRING)
    @Column(name = "object_type", length = 40)
    private AuditObjectType objectType;

    @Column(name = "object_id")
    private UUID objectId;

    protected SafetyEvent() {
    }

    public static SafetyEvent of(Instant now, UUID ownerId, String feature, Kind kind, ModerationCategory category,
                                 InjectionSignal signal, AuditObjectType objectType, UUID objectId) {
        SafetyEvent event = new SafetyEvent();
        event.id = UUID.randomUUID();
        event.occurredAt = now;
        event.ownerId = ownerId;
        event.feature = feature;
        event.kind = kind;
        event.category = category;
        event.signal = signal;
        event.objectType = objectType;
        event.objectId = objectId;
        return event;
    }

    public UUID getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getFeature() {
        return feature;
    }

    public Kind getKind() {
        return kind;
    }

    public ModerationCategory getCategory() {
        return category;
    }

    public InjectionSignal getSignal() {
        return signal;
    }

    public AuditObjectType getObjectType() {
        return objectType;
    }

    public UUID getObjectId() {
        return objectId;
    }
}
