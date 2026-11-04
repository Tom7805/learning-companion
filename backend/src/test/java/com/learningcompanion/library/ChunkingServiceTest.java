package com.learningcompanion.library;

import static org.assertj.core.api.Assertions.assertThat;

import com.learningcompanion.library.application.ingestion.ChunkingService;
import com.learningcompanion.library.application.ingestion.ChunkingService.Draft;
import com.learningcompanion.library.domain.ChunkLocation;
import com.learningcompanion.library.domain.ChunkLocation.Kind;
import com.learningcompanion.library.domain.DocumentChunk;
import com.learningcompanion.library.infrastructure.extractor.TextBlock;
import com.learningcompanion.library.infrastructure.extractor.TextBlock.Role;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** NCL-05-CN-003 — chia đoạn theo đề mục và độ dài, đoạn luôn gắn vị trí trong tài liệu gốc (QTN-20). */
class ChunkingServiceTest {

    private final ChunkingService chunking = new ChunkingService();

    @Test
    @DisplayName("Đề mục mở đoạn mới và được ghi kèm các đoạn bên dưới")
    void headingsStartNewChunks() {
        List<Draft> drafts = chunking.chunk(List.of(
                block(Kind.PARAGRAPH, 1, Role.HEADING, "Chương 1"),
                block(Kind.PARAGRAPH, 2, Role.BODY, "Đoạn một."),
                block(Kind.PARAGRAPH, 3, Role.BODY, "Đoạn hai."),
                block(Kind.PARAGRAPH, 4, Role.HEADING, "Chương 2"),
                block(Kind.PARAGRAPH, 5, Role.BODY, "Đoạn ba.")));

        assertThat(drafts).extracting(Draft::heading).containsExactly("Chương 1", "Chương 2");
        assertThat(drafts.getFirst().content()).isEqualTo("Đoạn một.\n\nĐoạn hai.");
        // Đoạn gộp hai đoạn văn liền nhau trỏ về cả khoảng 2–3.
        assertThat(drafts.getFirst().location()).isEqualTo(new ChunkLocation(Kind.PARAGRAPH, 2, 3));
        assertThat(drafts.get(1).location()).isEqualTo(ChunkLocation.of(Kind.PARAGRAPH, 5));
        assertThat(drafts.getFirst().embeddingText()).isEqualTo("Chương 1\nĐoạn một.\n\nĐoạn hai.");
    }

    @Test
    @DisplayName("Đoạn không bao giờ trải qua hai trang: mỗi đoạn trỏ về đúng một trang")
    void neverSpansPages() {
        List<Draft> drafts = chunking.chunk(List.of(
                block(Kind.PAGE, 1, Role.BODY, "Cuối trang một."),
                block(Kind.PAGE, 2, Role.BODY, "Đầu trang hai."),
                block(Kind.PAGE, 2, Role.BODY, "Vẫn trang hai.")));

        assertThat(drafts).extracting(Draft::location).containsExactly(ChunkLocation.of(Kind.PAGE, 1),
                ChunkLocation.of(Kind.PAGE, 2));
        assertThat(drafts.get(1).content()).isEqualTo("Đầu trang hai.\n\nVẫn trang hai.");
    }

    @Test
    @DisplayName("Đoạn văn dài được cắt ở ranh giới câu, mỗi phần không quá độ dài mục tiêu")
    void splitsLongParagraphAtSentences() {
        String sentence = "Đạo hàm đo tốc độ thay đổi tức thời của hàm số tại một điểm. ";
        List<Draft> drafts = chunking.chunk(List.of(block(Kind.PAGE, 7, Role.BODY, sentence.repeat(60))));

        assertThat(drafts).hasSizeGreaterThan(1)
                .allSatisfy(draft -> {
                    assertThat(draft.content().length()).isLessThanOrEqualTo(ChunkingService.TARGET);
                    assertThat(draft.content()).endsWith("điểm.");
                    assertThat(draft.location()).isEqualTo(ChunkLocation.of(Kind.PAGE, 7));
                });
    }

    @Test
    @DisplayName("Bảng là một khối liền: không bị cắt ngang, không gộp làm đoạn vượt độ dài mục tiêu")
    void keepsTablesWhole() {
        String table = "Năm | Dân số\n" + "2020 | 97 triệu\n".repeat(40);
        List<Draft> drafts = chunking.chunk(List.of(
                block(Kind.PARAGRAPH, 1, Role.BODY, "Bảng sau cho thấy dân số:"),
                block(Kind.PARAGRAPH, 2, Role.TABLE, table.strip()),
                block(Kind.PARAGRAPH, 3, Role.BODY, "Dân số tăng đều.")));

        assertThat(drafts).anySatisfy(draft -> assertThat(draft.content()).contains(table.strip()));
        // Bảng quá dài mới bị cắt theo dòng, không cắt giữa một hàng.
        String huge = ("2020 | " + "97 triệu ".repeat(10) + "\n").repeat(40).strip();
        List<Draft> split = chunking.chunk(List.of(block(Kind.SLIDE, 3, Role.TABLE, huge)));
        assertThat(split).hasSizeGreaterThan(1).allSatisfy(draft -> {
            assertThat(draft.content().length()).isLessThanOrEqualTo(ChunkingService.MAX);
            assertThat(draft.content().lines()).allMatch(line -> line.startsWith("2020 | "));
        });
    }

    @Test
    @DisplayName("Trang trình chiếu chỉ có tiêu đề vẫn là một đoạn; văn bản nhận dạng không gộp với lớp chữ")
    void keepsOrphanHeadingsAndSeparatesSources() {
        List<Draft> drafts = chunking.chunk(List.of(
                block(Kind.SLIDE, 1, Role.HEADING, "Cảm ơn"),
                block(Kind.SLIDE, 2, Role.HEADING, "Phụ lục"),
                block(Kind.SLIDE, 2, Role.BODY, "Tài liệu tham khảo.")));
        assertThat(drafts).extracting(Draft::content).containsExactly("Cảm ơn", "Tài liệu tham khảo.");
        assertThat(drafts.getFirst().location()).isEqualTo(ChunkLocation.of(Kind.SLIDE, 1));

        List<Draft> mixed = chunking.chunk(List.of(
                block(Kind.PARAGRAPH, 1, Role.BODY, "Chữ có sẵn."),
                new TextBlock(ChunkLocation.of(Kind.PARAGRAPH, 2), Role.BODY, "Chữ nhận dạng.",
                        DocumentChunk.Source.OCR)));
        assertThat(mixed).extracting(Draft::source).containsExactly(DocumentChunk.Source.TEXT,
                DocumentChunk.Source.OCR);
    }

    @Test
    @DisplayName("Không có chữ thì không có đoạn nào")
    void emptyInputHasNoChunks() {
        assertThat(chunking.chunk(List.of())).isEmpty();
        assertThat(chunking.chunk(List.of(block(Kind.PAGE, 1, Role.BODY, "   ")))).isEmpty();
    }

    private static TextBlock block(Kind kind, int number, Role role, String text) {
        return TextBlock.text(ChunkLocation.of(kind, number), role, text);
    }
}
