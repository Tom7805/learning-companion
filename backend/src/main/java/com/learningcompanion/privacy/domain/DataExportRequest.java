package com.learningcompanion.privacy.domain;

import com.learningcompanion.shared.ownership.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Một lần người học yêu cầu xuất toàn bộ dữ liệu (NCL-01-CN-008). */
@Entity
@Table(name = "data_exports")
public class DataExportRequest implements OwnedEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DataExportStatus status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "storage_key", length = 100)
    private String storageKey;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "file_count")
    private Integer fileCount;

    @Column(name = "file_name", length = 100)
    private String fileName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Section> sections = new ArrayList<>();

    @Column(name = "failure_reason", length = 60)
    private String failureReason;

    @Column(name = "download_count", nullable = false)
    private int downloadCount;

    @Version
    private long version;

    protected DataExportRequest() {
    }

    public static DataExportRequest queue(UUID ownerId, Instant now) {
        DataExportRequest request = new DataExportRequest();
        request.id = UUID.randomUUID();
        request.ownerId = ownerId;
        request.status = DataExportStatus.QUEUED;
        request.requestedAt = now;
        return request;
    }

    public void start(Instant now) {
        status = DataExportStatus.RUNNING;
        startedAt = now;
    }

    public void complete(String key, String name, long size, int files, List<Section> contents, Instant now,
                         Duration validFor) {
        status = DataExportStatus.READY;
        storageKey = key;
        fileName = name;
        sizeBytes = size;
        fileCount = files;
        sections = new ArrayList<>(contents);
        completedAt = now;
        expiresAt = now.plus(validFor);
    }

    public void fail(String reason, Instant now) {
        status = DataExportStatus.FAILED;
        failureReason = reason;
        completedAt = now;
    }

    /** Chuyển sang hết hạn và trả về khóa tệp cần xóa khỏi kho (nếu còn). */
    public Optional<String> expire() {
        String key = storageKey;
        status = DataExportStatus.EXPIRED;
        storageKey = null;
        return Optional.ofNullable(key);
    }

    public boolean isPastExpiry(Instant now) {
        return status == DataExportStatus.READY && expiresAt != null && !now.isBefore(expiresAt);
    }

    /** Luồng nền dừng giữa chừng (ví dụ máy chủ khởi động lại) thì yêu cầu không bao giờ xong; coi là thất bại. */
    public boolean isStalled(Instant now, Duration limit) {
        return status.inProgress() && !now.isBefore(requestedAt.plus(limit));
    }

    public void recordDownload() {
        downloadCount++;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getOwnerId() {
        return ownerId;
    }

    public DataExportStatus getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public Integer getFileCount() {
        return fileCount;
    }

    public String getFileName() {
        return fileName;
    }

    public List<Section> getSections() {
        return sections;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public int getDownloadCount() {
        return downloadCount;
    }

    /** Một phần dữ liệu trong gói: thư mục, tên hiển thị và số tệp. */
    public record Section(String id, String title, int fileCount) {
    }
}
