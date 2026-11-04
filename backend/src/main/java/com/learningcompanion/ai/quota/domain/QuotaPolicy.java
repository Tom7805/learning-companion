package com.learningcompanion.ai.quota.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Hạn mức chung cho mọi người học (NCL-04-CN-003): số lượt mỗi ngày, dung lượng lưu trữ và dung lượng tối đa một tệp
 * tải lên (NCL-05-CN-002). Chỉ có một dòng.
 */
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

    @Column(name = "max_upload_file_bytes", nullable = false)
    private long maxUploadFileBytes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private long version;

    protected QuotaPolicy() {
    }

    public void change(int dailyAiUnits, long storageLimitBytes, long maxUploadFileBytes, UUID actorId, Instant now) {
        this.dailyAiUnits = dailyAiUnits;
        this.storageLimitBytes = storageLimitBytes;
        this.maxUploadFileBytes = maxUploadFileBytes;
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    public int getDailyAiUnits() {
        return dailyAiUnits;
    }

    public long getStorageLimitBytes() {
        return storageLimitBytes;
    }

    /** Dung lượng tối đa một tệp tải lên (QTN-19). */
    public long getMaxUploadFileBytes() {
        return maxUploadFileBytes;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}
