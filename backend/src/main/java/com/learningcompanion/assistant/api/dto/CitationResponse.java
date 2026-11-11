package com.learningcompanion.assistant.api.dto;

import com.learningcompanion.assistant.application.CitationHighlighter;
import com.learningcompanion.assistant.domain.Citation;
import com.learningcompanion.library.domain.ChunkLocation;
import java.util.UUID;

/**
 * Một trích dẫn: số, tài liệu (trống nếu tài liệu đã bị xóa hẳn), vị trí và thứ tự đoạn để mở khung xem đúng chỗ
 * ({@code ?viewer=…&kind=…&number=…&passage=…}), đoạn gốc, phần liên quan cần tô sáng trong đoạn gốc và việc tài liệu
 * còn mở được không (NCL-07-CN-002).
 */
public record CitationResponse(UUID id, int number, UUID documentId, String documentTitle, Location location,
                               int passage, String excerpt, boolean available, Highlight highlight) {

    public record Location(String kind, int number, int end) {

        static Location of(ChunkLocation location) {
            return new Location(location.kind().name(), location.number(), location.end());
        }
    }

    /** Khoảng ký tự [start, end) của đoạn gốc liên quan nhất tới ý được trích. */
    public record Highlight(int start, int end) {
    }

    public static CitationResponse of(Citation citation, boolean available, CitationHighlighter.Range range) {
        return new CitationResponse(citation.getId(), citation.getNumber(),
                citation.getDocumentId(), citation.getDocumentTitle(), Location.of(citation.getLocation()),
                citation.getPassage(), citation.getExcerpt(), available && citation.getDocumentId() != null,
                range == null ? null : new Highlight(range.start(), range.end()));
    }

    /** Trích dẫn của câu trả lời {@code answer}: phần tô sáng tính theo câu chứa số trích dẫn. */
    public static CitationResponse of(Citation citation, boolean available, String answer) {
        return of(citation, available, CitationHighlighter.highlight(answer, citation.getNumber(),
                citation.getExcerpt()));
    }
}
