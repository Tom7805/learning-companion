package com.learningcompanion.library.api.dto;

import com.learningcompanion.library.domain.Document;
import com.learningcompanion.library.domain.DocumentStatus;
import java.util.List;
import java.util.UUID;

/** Một lần mở khung xem: trạng thái và các đoạn có vị trí, không bao giờ kèm vector. */
public record ViewerContentResponse(UUID id, String title, String type, String status, String mimeType,
                                    int percent, ProcessingInfo processing, List<DocumentChunkResponse> chunks) {
    public static ViewerContentResponse of(Document document, List<DocumentChunkResponse> chunks) {
        int percent = document.getStatus() == DocumentStatus.READY ? 100 : document.getProcessingPercent();
        return new ViewerContentResponse(document.getId(), document.getTitle(), document.getType().name(),
                document.getStatus().name(), document.getMimeType(), percent, ProcessingInfo.of(document), chunks);
    }
}
