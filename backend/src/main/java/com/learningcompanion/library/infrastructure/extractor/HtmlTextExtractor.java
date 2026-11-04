package com.learningcompanion.library.infrastructure.extractor;

import com.learningcompanion.library.domain.DocumentType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Trang web đã lưu: đoạn văn được đánh số theo thứ tự trong trang. */
@Component
public class HtmlTextExtractor implements TextExtractor {

    @Override
    public Set<DocumentType> types() {
        return Set.of(DocumentType.WEB);
    }

    @Override
    public Extraction extract(byte[] file, String mimeType, PageRecognizer recognizer) {
        List<TextBlock> blocks = new ArrayList<>();
        HtmlBlocks.read(new String(file, StandardCharsets.UTF_8), blocks);
        return new Extraction(blocks, blocks.size());
    }
}
