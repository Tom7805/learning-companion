package com.learningcompanion.library.api.dto;

import com.learningcompanion.library.domain.Document;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Một dòng trong kho tài liệu: không kèm nội dung để danh sách nhẹ. */
public record DocumentCardResponse(UUID id, String title, String type, String originalFilename, String mimeType,
                                   long sizeBytes, Instant createdAt, UUID folderId, List<UUID> tagIds,
                                   boolean starred, String status, Instant lastOpenedAt) {

    public static DocumentCardResponse of(Document document) {
        return new DocumentCardResponse(document.getId(), document.getTitle(), document.getType().name(),
                document.getOriginalFilename(), document.getMimeType(), document.getSizeBytes(),
                document.getCreatedAt(), document.getFolderId(), document.getTagIds().stream().sorted().toList(),
                document.isStarred(), document.getStatus().name(), document.getLastOpenedAt());
    }
}
