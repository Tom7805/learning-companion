package com.learningcompanion.library.application.ingestion;

import com.learningcompanion.library.domain.DocumentType;
import com.learningcompanion.library.infrastructure.extractor.Extraction;
import com.learningcompanion.library.infrastructure.extractor.PageRecognizer;
import com.learningcompanion.library.infrastructure.extractor.TextExtractor;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Chọn bộ trích văn bản theo loại tài liệu. */
@Service
public class TextExtractionService {

    private final Map<DocumentType, TextExtractor> extractors = new EnumMap<>(DocumentType.class);

    public TextExtractionService(List<TextExtractor> all) {
        for (TextExtractor extractor : all) {
            for (DocumentType type : extractor.types()) {
                if (extractors.put(type, extractor) != null) {
                    throw new IllegalStateException("Hai bộ trích văn bản cho cùng loại " + type);
                }
            }
        }
    }

    public boolean supports(DocumentType type) {
        return extractors.containsKey(type);
    }

    public Optional<Extraction> extract(DocumentType type, byte[] file, String mimeType, PageRecognizer recognizer) {
        TextExtractor extractor = extractors.get(type);
        return extractor == null ? Optional.empty() : Optional.of(extractor.extract(file, mimeType, recognizer));
    }
}
