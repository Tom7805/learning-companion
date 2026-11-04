package com.learningcompanion.library.infrastructure.extractor;

import com.learningcompanion.library.domain.DocumentType;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Văn bản thuần và Markdown: đoạn văn cách nhau bằng dòng trống, đề mục "#", bảng "|", công thức "$$", mã "```". */
@Component
public class PlainTextExtractor implements TextExtractor {

    @Override
    public Set<DocumentType> types() {
        return Set.of(DocumentType.TEXT);
    }

    @Override
    public Extraction extract(byte[] file, String mimeType, PageRecognizer recognizer) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(file)).toString();
        } catch (CharacterCodingException ex) {
            throw new UnreadableDocumentException("Văn bản không phải UTF-8", ex);
        }
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<TextBlock> blocks = Paragraphs.numbered(text);
        return new Extraction(blocks, blocks.size());
    }
}
