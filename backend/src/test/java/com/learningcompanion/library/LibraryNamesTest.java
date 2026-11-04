package com.learningcompanion.library;

import static org.assertj.core.api.Assertions.assertThat;

import com.learningcompanion.library.application.LibraryNames;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** NCL-05-CN-001 TC-03 — quy tắc tên trùng trong kho tài liệu và tên gợi ý. */
class LibraryNamesTest {

    @Test
    @DisplayName("Gọn khoảng trắng và chuẩn hóa Unicode: chữ có dấu gõ dựng sẵn hay tổ hợp đều là một tên")
    void cleansAndNormalizes() {
        String decomposed = "Giải tích";
        assertThat(LibraryNames.clean("  Giải   tích \t")).isEqualTo("Giải tích");
        assertThat(LibraryNames.key(decomposed)).isEqualTo(LibraryNames.key("GIẢI TÍCH"));
        assertThat(LibraryNames.clean(null)).isEmpty();
    }

    @Test
    @DisplayName("Gợi ý tên kế tiếp còn trống, tiếp nối số thứ tự đã có")
    void suggestsNextFreeName() {
        Set<String> taken = Set.of("giải tích", "giải tích (2)");
        assertThat(LibraryNames.suggest("Giải tích", taken, 120)).isEqualTo("Giải tích (3)");
        assertThat(LibraryNames.suggest("Giải tích (2)", taken, 120)).isEqualTo("Giải tích (3)");
        assertThat(LibraryNames.suggest("Vật lý", Set.of("vật lý"), 120)).isEqualTo("Vật lý (2)");
        assertThat(LibraryNames.suggest("(2)", Set.of("(2)"), 120)).isEqualTo("(2) (2)");
    }

    @Test
    @DisplayName("Tên gợi ý không vượt độ dài tối đa")
    void suggestionFitsMaxLength() {
        String longName = "a".repeat(40);
        String suggestion = LibraryNames.suggest(longName, Set.of(longName), 40);
        assertThat(suggestion).hasSizeLessThanOrEqualTo(40).endsWith(" (2)");
    }
}
