package com.learningcompanion.library.api.dto;

import com.learningcompanion.library.domain.Document;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Chi tiết tài liệu kèm toàn bộ nội dung; chỉ trả cho chính chủ sở hữu. */
public record DocumentResponse(UUID id, String title, String type, String contentText, String originalFilename,
                               String mimeType, long sizeBytes, Instant createdAt, Instant updatedAt, UUID folderId,
                               List<UUID> tagIds, boolean starred, String status, Instant lastOpenedAt,
                                   ProcessingInfo processing, String sourceUrl, Instant fetchedAt) {

    public static DocumentResponse of(Document document) {
        return new DocumentResponse(document.getId(), document.getTitle(), document.getType().name(),
                document.getContentText(), document.getOriginalFilename(), document.getMimeType(),
                document.getSizeBytes(), document.getCreatedAt(), document.getUpdatedAt(), document.getFolderId(),
                document.getTagIds().stream().sorted().toList(), document.isStarred(), document.getStatus().name(),
                document.getLastOpenedAt(), ProcessingInfo.of(document), document.getSourceUrl(), document.getFetchedAt());
    }
}
