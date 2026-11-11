package com.learningcompanion.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.learningcompanion.ai.rag.TextTerms;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tách câu trả lời theo luồng, đọc số trích dẫn và tách tiếng để so khớp (NCL-07-CN-001), không cần cơ sở dữ liệu. */
class AnswerSplitterTest {

    @Test
    @DisplayName("Khối câu hỏi gợi ý không bao giờ lọt vào phần hiện dần, kể cả khi dòng mở khối bị cắt giữa các đoạn")
    void splitsSuggestionsOutOfStream() {
        List<String> shown = new ArrayList<>();
        AnswerSplitter splitter = new AnswerSplitter(shown::add);
        for (String piece : List.of("Duyệt theo thứ tự giữa ", "thăm cây con trái trước [1].", "\nCâu hỏi ",
                "gợi ý:\n- Duyệt theo thứ tự trước là gì?\n", "- Cho ví dụ.\n* Khi nào dùng cây nhị phân?\n- Thừa")) {
            splitter.accept(piece);
        }
        splitter.finish();

        assertThat(String.join("", shown)).isEqualTo("Duyệt theo thứ tự giữa thăm cây con trái trước [1].\n");
        assertThat(splitter.shown()).isEqualTo("Duyệt theo thứ tự giữa thăm cây con trái trước [1].");
        assertThat(splitter.suggestions()).containsExactly("Duyệt theo thứ tự trước là gì?", "Cho ví dụ.",
                "Khi nào dùng cây nhị phân?");
    }

    @Test
    @DisplayName("Không có khối gợi ý thì mọi chữ đều hiện, kể cả dòng cuối không xuống dòng")
    void withoutSuggestionsEverythingIsShown() {
        List<String> shown = new ArrayList<>();
        AnswerSplitter splitter = new AnswerSplitter(shown::add);
        splitter.accept("Dòng một.\nDòng hai không có dấu xuống dòng");
        splitter.finish();
        assertThat(String.join("", shown)).isEqualTo("Dòng một.\nDòng hai không có dấu xuống dòng");
        assertThat(splitter.suggestions()).isEmpty();
    }

    @Test
    @DisplayName("Dòng mở khối gợi ý chấp nhận chữ đậm, tiếng Anh và thiếu dấu hai chấm")
    void markerVariants() {
        for (String marker : List.of("**Câu hỏi gợi ý:**", "Suggested questions:", "câu hỏi gợi ý")) {
            AnswerSplitter splitter = new AnswerSplitter(text -> { });
            splitter.accept("Trả lời [1].\n" + marker + "\n1. Một?\n2) Hai?\n- Ba?");
            splitter.finish();
            assertThat(splitter.shown()).isEqualTo("Trả lời [1].");
            assertThat(splitter.suggestions()).containsExactly("Một?", "Hai?", "Ba?");
        }
    }

    @Test
    @DisplayName("Số trích dẫn: đọc [1], [2, 3], bỏ số ngoài khoảng nguồn")
    void citationNumbers() {
        assertThat(CitationNumbers.in("Ý một [1]. Ý hai [2, 3][3]. Năm [9]. Năm 2024.", 3)).containsExactly(1, 2, 3);
        assertThat(CitationNumbers.in("Không trích dẫn", 3)).isEmpty();
    }

    @Test
    @DisplayName("Tách tiếng: giữ dấu, bỏ hư từ; cặp tiếng liền nhau; nhận ra câu gõ không dấu")
    void textTerms() {
        List<String> words = TextTerms.words("Cách duyệt cây theo thứ tự giữa là gì?");
        assertThat(words).containsExactly("duyệt", "cây", "thứ", "tự", "giữa");
        assertThat(TextTerms.pairs(words)).contains("duyệt cây", "thứ tự");
        assertThat(TextTerms.unaccented(words)).isFalse();
        assertThat(TextTerms.unaccented(TextTerms.words("dao ham rieng"))).isTrue();
        assertThat(TextTerms.fold("đạo")).isEqualTo("dao");
        // Giữ dấu để "mạng" không khớp nhầm "mang".
        assertThat(TextTerms.words("mạng nơ ron")).doesNotContain("mang");
    }

    @Test
    @DisplayName("Câu hỏi gợi ý thiếu thì bổ sung theo tài liệu, luôn đủ ba câu")
    void titleOfConversation() {
        assertThat(com.learningcompanion.assistant.domain.Conversation.titleOf("  Một   câu  hỏi ")).isEqualTo(
                "Một câu hỏi");
        assertThat(com.learningcompanion.assistant.domain.Conversation.titleOf("từ ".repeat(80))).endsWith("…")
                .hasSizeLessThanOrEqualTo(120);
    }
}
