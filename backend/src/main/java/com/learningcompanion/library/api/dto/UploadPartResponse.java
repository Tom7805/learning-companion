package com.learningcompanion.library.api.dto;

import com.learningcompanion.library.domain.UploadSession;
import java.time.Instant;
import java.util.UUID;

/**
 * Tiến độ một lần tải: giao diện gửi tiếp từ phần {@code nextPart} (byte thứ {@code receivedBytes}); mỗi phần dài
 * {@code chunkSize} byte, riêng phần cuối là phần còn lại. {@code documentId} có khi lần tải đã hoàn tất.
 */
public record UploadPartResponse(UUID id, String fileName, UUID folderId, long sizeBytes, int chunkSize,
                                 int partCount, long receivedBytes, int nextPart, String status, UUID documentId,
                                 Instant expiresAt) {

    public static UploadPartResponse of(UploadSession session) {
        return new UploadPartResponse(session.getId(), session.getFileName(), session.getFolderId(),
                session.getSizeBytes(), session.getChunkSize(), session.partCount(), session.getReceivedBytes(),
                session.nextPart(), session.getStatus().name(), session.getDocumentId(), session.getExpiresAt());
    }
}
