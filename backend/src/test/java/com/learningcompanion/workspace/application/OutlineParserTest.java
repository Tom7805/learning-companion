package com.learningcompanion.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutlineParserTest {

    @Test
    @DisplayName("đọc cấp, tên, mục tiêu học và số nguồn hợp lệ; bỏ dòng không theo định dạng")
    void parsesLines() {
        List<OutlineParser.Line> lines = OutlineParser.parse("""
                Dàn ý đề xuất:
                # Cấu trúc tế bào :: Mô tả được các thành phần của tế bào [1][2]
                ## **Màng tế bào** :: Giải thích được vai trò của màng [1, 9]
                ### Kênh ion :: Nêu được cơ chế vận chuyển [3]
                - một dòng thừa
                # Hô hấp tế bào
                """, 3);

        assertThat(lines).extracting(OutlineParser.Line::depth).containsExactly(1, 2, 3, 1);
        assertThat(lines.get(0).title()).isEqualTo("Cấu trúc tế bào");
        assertThat(lines.get(0).goal()).isEqualTo("Mô tả được các thành phần của tế bào");
        assertThat(lines.get(0).sources()).containsExactly(1, 2);
        assertThat(lines.get(1).title()).isEqualTo("Màng tế bào");
        assertThat(lines.get(1).sources()).containsExactly(1);
        assertThat(lines.get(3).goal()).isEmpty();
    }

    @Test
    @DisplayName("không bao giờ vượt ba cấp và không nhảy cóc cấp")
    void clampsDepth() {
        List<OutlineParser.Line> lines = OutlineParser.parse("""
                ## Bắt đầu ở cấp hai
                #### Nhảy xuống cấp bốn
                ###### Sâu hơn nữa
                """, 0);

        assertThat(lines).extracting(OutlineParser.Line::depth).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("khóa so sánh tên không phân biệt hoa thường và khoảng trắng")
    void keyIgnoresCaseAndSpaces() {
        assertThat(OutlineParser.key("  Màng   Tế bào ")).isEqualTo(OutlineParser.key("màng tế bào"));
    }
}
