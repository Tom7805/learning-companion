package com.learningcompanion.ai.quota.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Hạn mức chung cho mọi người học (NCL-04-CN-003): số lượt mỗi ngày và dung lượng lưu trữ. Chỉ có một dòng. */
@Entity
@Table(name = "quota_policies")
public class QuotaPolicy {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(name = "daily_ai_units", nullable = false)
    private int dailyAiUnits;

    @Column(name = "storage_limit_bytes", nullable = false)
    private long storageLimitBytes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private long version;

    protected QuotaPolicy() {
    }

    public void change(int dailyAiUnits, long storageLimitBytes, UUID actorId, Instant now) {
        this.dailyAiUnits = dailyAiUnits;
        this.storageLimitBytes = storageLimitBytes;
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    public int getDailyAiUnits() {
        return dailyAiUnits;
    }

    public long getStorageLimitBytes() {
        return storageLimitBytes;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}
