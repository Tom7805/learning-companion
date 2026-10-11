package com.learningcompanion.library.api.dto;

import com.learningcompanion.library.domain.Document;
import java.time.Instant;
import java.util.UUID;

/** Chi tiết tài liệu kèm toàn bộ nội dung; chỉ trả cho chính chủ sở hữu. */
public record DocumentResponse(UUID id, String title, String type, String contentText, String originalFilename,
                               String mimeType, long sizeBytes, Instant createdAt, Instant updatedAt) {

    public static DocumentResponse of(Document document) {
        return new DocumentResponse(document.getId(), document.getTitle(), document.getType().name(),
                document.getContentText(), document.getOriginalFilename(), document.getMimeType(),
                document.getSizeBytes(), document.getCreatedAt(), document.getUpdatedAt());
    }
}
