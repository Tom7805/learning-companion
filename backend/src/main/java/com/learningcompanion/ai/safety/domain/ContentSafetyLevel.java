package com.learningcompanion.ai.safety.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Ngưỡng lọc hiện hành của một nhóm nội dung (NCL-04-CN-004). */
@Entity
@Table(name = "content_safety_levels")
public class ContentSafetyLevel {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ModerationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SafetyLevel level;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private long version;

    protected ContentSafetyLevel() {
    }

    public void change(SafetyLevel level, UUID actorId, Instant now) {
        this.level = level;
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    public ModerationCategory getCategory() {
        return category;
    }

    public SafetyLevel getLevel() {
        return level;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}
