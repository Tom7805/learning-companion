package com.learningcompanion.library.api.dto;

import com.learningcompanion.library.domain.Document;
import java.time.Instant;
import java.util.UUID;

/** Một dòng trong thư viện: không kèm nội dung để danh sách nhẹ. */
public record DocumentCardResponse(UUID id, String title, String type, String originalFilename, long sizeBytes,
                                   Instant createdAt) {

    public static DocumentCardResponse of(Document document) {
        return new DocumentCardResponse(document.getId(), document.getTitle(), document.getType().name(),
                document.getOriginalFilename(), document.getSizeBytes(), document.getCreatedAt());
    }
}
