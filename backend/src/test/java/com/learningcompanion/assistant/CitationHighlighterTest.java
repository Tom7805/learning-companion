package com.learningcompanion.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.learningcompanion.assistant.application.CitationHighlighter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Phần cần tô sáng của đoạn gốc (NCL-07-CN-002), không cần cơ sở dữ liệu. */
class CitationHighlighterTest {

    private static final String EXCERPT = "Cây nhị phân có tối đa hai con. Duyệt theo thứ tự giữa thăm cây con trái "
            + "trước, rồi tới gốc. Duyệt theo thứ tự trước thăm gốc trước.";

    @Test
    @DisplayName("Tô sáng câu của đoạn gốc khớp nhất với câu trả lời mang số trích dẫn đó")
    void highlightsBestSentence() {
        String answer = "Cây nhị phân là cấu trúc dữ liệu [2]. Duyệt theo thứ tự giữa thăm cây con trái trước [1].";
        CitationHighlighter.Range range = CitationHighlighter.highlight(answer, 1, EXCERPT);
        assertThat(EXCERPT.substring(range.start(), range.end()))
                .isEqualTo("Duyệt theo thứ tự giữa thăm cây con trái trước, rồi tới gốc.");
    }

    @Test
    @DisplayName("Số trích dẫn nằm trong nhóm [1, 2] hay đứng sau dấu chấm vẫn tìm đúng câu")
    void groupedAndTrailingMarkers() {
        CitationHighlighter.Range grouped = CitationHighlighter.highlight(
                "Duyệt theo thứ tự trước thăm gốc trước [1, 2].", 2, EXCERPT);
        assertThat(EXCERPT.substring(grouped.start(), grouped.end())).startsWith("Duyệt theo thứ tự trước");
        CitationHighlighter.Range trailing = CitationHighlighter.highlight(
                "Cây nhị phân có tối đa hai con. [1]", 1, EXCERPT);
        assertThat(EXCERPT.substring(trailing.start(), trailing.end())).isEqualTo("Cây nhị phân có tối đa hai con.");
    }

    @Test
    @DisplayName("Không câu nào khớp đủ thì không tô sáng")
    void noMatch() {
        assertThat(CitationHighlighter.highlight("Mạng nơ ron [1].", 1, EXCERPT)).isNull();
        assertThat(CitationHighlighter.highlight(null, 1, EXCERPT)).isNull();
    }
}
