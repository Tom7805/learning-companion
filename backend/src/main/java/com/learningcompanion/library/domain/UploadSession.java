package com.learningcompanion.library.domain;

import com.learningcompanion.shared.ownership.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Một lần tải tệp lên kho (NCL-05-CN-002). Tệp được gửi thành các phần cỡ {@code chunkSize} theo thứ tự; phiên chỉ ghi
 * số byte đã nhận để giao diện hỏi lại và tải tiếp từ phần còn thiếu sau khi mạng có lại (TC-04). Ghép xong thì phiên
 * giữ mã tài liệu đã tạo, để gửi lại yêu cầu hoàn tất (khi mất kết nối lúc chờ trả lời) vẫn nhận đúng tài liệu đó.
 */
@Entity
@Table(name = "library_upload_sessions")
public class UploadSession implements OwnedEntity {

    public enum Status { UPLOADING, COMPLETED }

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "folder_id", updatable = false)
    private UUID folderId;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "chunk_size", nullable = false, updatable = false)
    private int chunkSize;

    @Column(name = "received_bytes", nullable = false)
    private long receivedBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Version
    private long version;

    protected UploadSession() {
    }

    public static UploadSession start(UUID ownerId, UUID folderId, String fileName, long sizeBytes, int chunkSize,
                                      Instant now, Instant expiresAt) {
        UploadSession session = new UploadSession();
        session.id = UUID.randomUUID();
        session.ownerId = ownerId;
        session.folderId = folderId;
        session.fileName = fileName;
        session.sizeBytes = sizeBytes;
        session.chunkSize = chunkSize;
        session.status = Status.UPLOADING;
        session.createdAt = now;
        session.updatedAt = now;
        session.expiresAt = expiresAt;
        return session;
    }

    /** Số phần của cả tệp. */
    public int partCount() {
        return (int) ((sizeBytes + chunkSize - 1) / chunkSize);
    }

    /** Số byte phần thứ {@code index} phải có: mọi phần đủ cỡ, riêng phần cuối là phần còn lại. */
    public int expectedLength(int index) {
        long start = (long) index * chunkSize;
        return (int) Math.min(chunkSize, sizeBytes - start);
    }

    /** Phần kế tiếp còn thiếu; nhận đủ thì bằng số phần (phần cuối có thể ngắn hơn cỡ phần). */
    public int nextPart() {
        return isFullyReceived() ? partCount() : (int) (receivedBytes / chunkSize);
    }

    public boolean isFullyReceived() {
        return receivedBytes == sizeBytes;
    }

    /** Ghi nhận một phần vừa nhận; gửi lại phần đã có (sau khi mất kết nối) không làm đổi tiến độ. */
    public void acceptPart(int index, Instant now, Instant newExpiry) {
        if (index == nextPart() && !isFullyReceived()) {
            receivedBytes += expectedLength(index);
        }
        updatedAt = now;
        expiresAt = newExpiry;
    }

    public void complete(UUID createdDocumentId, Instant now) {
        status = Status.COMPLETED;
        documentId = createdDocumentId;
        updatedAt = now;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getFolderId() {
        return folderId;
    }

    public String getFileName() {
        return fileName;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public long getReceivedBytes() {
        return receivedBytes;
    }

    public Status getStatus() {
        return status;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
