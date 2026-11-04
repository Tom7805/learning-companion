package com.learningcompanion.library.infrastructure.extractor;

import com.learningcompanion.library.domain.ChunkLocation;
import com.learningcompanion.library.domain.DocumentChunk;
import java.util.Objects;

/** Một khối văn bản liền mạch (đề mục, đoạn văn, bảng, công thức, mã) cùng vị trí của nó trong tài liệu gốc. */
public record TextBlock(ChunkLocation location, Role role, String text, DocumentChunk.Source source) {

    public enum Role {
        HEADING,
        BODY,
        /** Bảng: mỗi hàng một dòng, các ô cách nhau bằng " | ". */
        TABLE,
        /** Công thức toán, giữ nguyên ký hiệu. */
        FORMULA,
        /** Đoạn mã hay văn bản định dạng sẵn. */
        CODE;

        /** Khối giữ nguyên cấu trúc: chia đoạn không cắt ngang nếu không quá dài. */
        public boolean atomic() {
            return this == TABLE || this == FORMULA || this == CODE;
        }
    }

    public TextBlock {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(source, "source");
        text = text == null ? "" : text.strip();
    }

    public static TextBlock text(ChunkLocation location, Role role, String text) {
        return new TextBlock(location, role, text, DocumentChunk.Source.TEXT);
    }
}
