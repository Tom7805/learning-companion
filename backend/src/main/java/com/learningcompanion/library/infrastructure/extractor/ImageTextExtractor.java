package com.learningcompanion.library.infrastructure.extractor;

import com.learningcompanion.library.domain.ChunkLocation;
import com.learningcompanion.library.domain.DocumentChunk;
import com.learningcompanion.library.domain.DocumentType;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Ảnh (ảnh chụp vở, ảnh quét): toàn bộ chữ đi qua nhận dạng chữ; mọi đoạn trỏ về chính ảnh gốc (NCL-05-CN-003 TC-02).
 */
@Component
public class ImageTextExtractor implements TextExtractor {

    @Override
    public Set<DocumentType> types() {
        return Set.of(DocumentType.IMAGE);
    }

    @Override
    public Extraction extract(byte[] file, String mimeType, PageRecognizer recognizer) {
        String text = recognizer.recognize(1, file, mimeType);
        return new Extraction(Paragraphs.split(text, ChunkLocation.of(ChunkLocation.Kind.IMAGE, 1),
                DocumentChunk.Source.OCR), 1);
    }
}
