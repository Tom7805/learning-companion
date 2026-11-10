package com.learningcompanion.assistant.api.dto;

import com.learningcompanion.assistant.domain.Citation;
import com.learningcompanion.library.domain.ChunkLocation;
import java.util.UUID;

/**
 * Một trích dẫn: số, tài liệu (trống nếu tài liệu đã bị xóa hẳn), vị trí và thứ tự đoạn để mở khung xem đúng chỗ
 * ({@code ?viewer=…&kind=…&number=…&passage=…}), và đoạn gốc.
 */
public record CitationResponse(UUID id, int number, UUID documentId, String documentTitle, Location location,
                               int passage, String excerpt) {

    public record Location(String kind, int number, int end) {

        static Location of(ChunkLocation location) {
            return new Location(location.kind().name(), location.number(), location.end());
        }
    }

    public static CitationResponse of(Citation citation) {
        return new CitationResponse(citation.getId(), citation.getNumber(), citation.getDocumentId(),
                citation.getDocumentTitle(), Location.of(citation.getLocation()), citation.getPassage(),
                citation.getExcerpt());
    }
}
