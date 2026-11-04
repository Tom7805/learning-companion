package com.learningcompanion.library.infrastructure.extractor;

import java.util.List;

/**
 * Văn bản trích được, theo thứ tự đọc. {@code unitCount}: số trang, số trang trình chiếu, số ảnh hoặc số đoạn văn của
 * tài liệu.
 */
public record Extraction(List<TextBlock> blocks, int unitCount) {

    public Extraction {
        blocks = List.copyOf(blocks);
    }

    public String fullText() {
        StringBuilder text = new StringBuilder();
        for (TextBlock block : blocks) {
            if (!text.isEmpty()) {
                text.append("\n\n");
            }
            text.append(block.text());
        }
        return text.toString();
    }
}
