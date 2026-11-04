package com.learningcompanion.library.api.dto;

import com.learningcompanion.library.domain.Document;
import java.time.Instant;

/**
 * Kết quả xử lý của tài liệu (NCL-05-CN-003): lý do lỗi và trang lỗi, thử lại được không, số trang (trang trình chiếu,
 * ảnh, đoạn văn) và số đoạn đã lập chỉ mục, lúc xử lý xong.
 */
public record ProcessingInfo(String error, Integer errorPage, boolean retryable, Integer units, int chunks,
                             Instant processedAt) {

    public static ProcessingInfo of(Document document) {
        return new ProcessingInfo(document.getProcessingError() == null ? null : document.getProcessingError().name(),
                document.getProcessingErrorPage(),
                document.getProcessingError() != null && document.getProcessingError().retryable(),
                document.getUnitCount(), document.getChunkCount(), document.getProcessedAt());
    }
}
