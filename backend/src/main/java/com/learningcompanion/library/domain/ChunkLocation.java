package com.learningcompanion.library.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;

/**
 * Vị trí của một đoạn trong tài liệu gốc (QTN-20): số trang, số trang trình chiếu, thứ tự ảnh, hoặc khoảng đoạn văn
 * (từ đoạn {@code number} tới đoạn {@code end}). Trang, trang trình chiếu và ảnh luôn có {@code end == number}.
 */
@Embeddable
public record ChunkLocation(
        @Enumerated(EnumType.STRING) @Column(name = "location_kind", nullable = false, length = 16) Kind kind,
        @Column(name = "location_number", nullable = false) int number,
        @Column(name = "location_end", nullable = false) int end) {

    public enum Kind {
        /** Trang của tài liệu dạng trang. */
        PAGE,
        /** Trang của tệp trình chiếu, theo thứ tự trình chiếu. */
        SLIDE,
        /** Ảnh (ảnh chụp vở, ảnh quét); tài liệu ảnh có một ảnh. */
        IMAGE,
        /** Đoạn văn của tài liệu Word, sách điện tử hay văn bản thuần. */
        PARAGRAPH
    }

    public ChunkLocation {
        Objects.requireNonNull(kind, "kind");
        if (number < 1 || end < number) {
            throw new IllegalArgumentException("Vị trí không hợp lệ: " + number + "-" + end);
        }
        if (kind != Kind.PARAGRAPH && end != number) {
            throw new IllegalArgumentException("Chỉ đoạn văn mới trải qua nhiều vị trí");
        }
    }

    public static ChunkLocation of(Kind kind, int number) {
        return new ChunkLocation(kind, number, number);
    }

    /** Vị trí gộp từ vị trí này tới {@code last} (cùng loại, đoạn văn). */
    public ChunkLocation through(ChunkLocation last) {
        if (last.kind != kind) {
            throw new IllegalArgumentException("Không gộp được hai loại vị trí khác nhau");
        }
        return new ChunkLocation(kind, Math.min(number, last.number), Math.max(end, last.end));
    }
}
