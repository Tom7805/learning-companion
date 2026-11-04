package com.learningcompanion.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.learningcompanion.devtools.SampleDocuments;
import com.learningcompanion.library.domain.ChunkLocation;
import com.learningcompanion.library.domain.ChunkLocation.Kind;
import com.learningcompanion.library.domain.DocumentChunk;
import com.learningcompanion.library.infrastructure.extractor.DocxTextExtractor;
import com.learningcompanion.library.infrastructure.extractor.EpubTextExtractor;
import com.learningcompanion.library.infrastructure.extractor.Extraction;
import com.learningcompanion.library.infrastructure.extractor.HtmlTextExtractor;
import com.learningcompanion.library.infrastructure.extractor.ImageTextExtractor;
import com.learningcompanion.library.infrastructure.extractor.PageRecognizer;
import com.learningcompanion.library.infrastructure.extractor.PdfTextExtractor;
import com.learningcompanion.library.infrastructure.extractor.PlainTextExtractor;
import com.learningcompanion.library.infrastructure.extractor.PptxTextExtractor;
import com.learningcompanion.library.infrastructure.extractor.TextBlock;
import com.learningcompanion.library.infrastructure.extractor.TextBlock.Role;
import com.learningcompanion.library.infrastructure.extractor.UnreadableDocumentException;
import com.learningcompanion.shared.storage.TestFiles;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** NCL-05-CN-003 — trích văn bản theo từng loại tệp, mỗi khối gắn đúng vị trí trong tài liệu gốc. */
class TextExtractorsTest {

    private final List<Integer> recognized = new ArrayList<>();
    private final PageRecognizer recognizer = (number, image, mimeType) -> {
        recognized.add(number);
        assertThat(mimeType).startsWith("image/");
        return "Chữ nhận dạng ở trang " + number + ".";
    };

    @Test
    @DisplayName("Tài liệu dạng trang: lớp chữ gắn số trang; chỉ trang quét đi qua nhận dạng chữ")
    void pdfUsesTextLayerAndRecognizesScannedPages() {
        Extraction extraction = new PdfTextExtractor().extract(SampleDocuments.lecture(5, 3), "application/pdf",
                recognizer);

        assertThat(extraction.unitCount()).isEqualTo(5);
        assertThat(recognized).containsExactly(3);
        assertThat(extraction.blocks()).extracting(block -> block.location().number())
                .containsOnly(1, 2, 3, 4, 5).isSorted();
        assertThat(extraction.blocks()).filteredOn(block -> block.location().number() == 3)
                .extracting(TextBlock::source).containsOnly(DocumentChunk.Source.OCR);
        assertThat(extraction.blocks()).filteredOn(block -> block.location().number() == 2)
                .first().satisfies(block -> {
                    assertThat(block.role()).isEqualTo(Role.HEADING);
                    assertThat(block.text()).isEqualTo("Chuong 2");
                });
    }

    @Test
    @DisplayName("Tài liệu chỉ có trang trắng: không có khối nào và không gọi nhận dạng chữ")
    void blankPdfHasNoText() {
        Extraction extraction = new PdfTextExtractor().extract(SampleDocuments.blank(3), "application/pdf",
                recognizer);
        assertThat(extraction.blocks()).isEmpty();
        assertThat(extraction.unitCount()).isEqualTo(3);
        assertThat(recognized).isEmpty();
    }

    @Test
    @DisplayName("Tệp hỏng báo không đọc được")
    void corruptFilesAreUnreadable() {
        assertThatThrownBy(() -> new PdfTextExtractor().extract(TestFiles.ascii("%PDF-1.4 hỏng"), "application/pdf",
                recognizer)).isInstanceOf(UnreadableDocumentException.class);
        assertThatThrownBy(() -> new DocxTextExtractor().extract(TestFiles.zip(java.util.Map.of("a.txt",
                TestFiles.ascii("x"))), "", recognizer)).isInstanceOf(UnreadableDocumentException.class);
    }

    @Test
    @DisplayName("Tài liệu Word: đề mục theo kiểu đoạn, bảng giữ hàng và ô, công thức giữ ký hiệu, đánh số đoạn văn")
    void docxKeepsStructure() {
        Extraction extraction = new DocxTextExtractor().extract(ExtractorSamples.docx(), "", recognizer);

        assertThat(extraction.blocks()).extracting(TextBlock::role).containsExactly(Role.HEADING, Role.BODY,
                Role.TABLE, Role.FORMULA, Role.HEADING, Role.BODY);
        assertThat(extraction.blocks()).extracting(block -> block.location().number())
                .containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(extraction.blocks().get(1).text()).isEqualTo("Giới hạn mô tả giá trị mà hàm số tiến tới.");
        assertThat(extraction.blocks().get(2).text()).isEqualTo("Hàm số | Giới hạn\nsin x / x | 1");
        assertThat(extraction.blocks().get(3).text()).isEqualTo("lim(1+1/n)^n = e");
        assertThat(extraction.blocks()).allMatch(block -> block.location().kind() == Kind.PARAGRAPH);
    }

    @Test
    @DisplayName("Tệp trình chiếu: theo đúng thứ tự trình chiếu, tiêu đề là đề mục đứng đầu trang, bảng giữ cấu trúc")
    void pptxFollowsSlideOrder() {
        Extraction extraction = new PptxTextExtractor().extract(ExtractorSamples.pptx(), "", recognizer);

        assertThat(extraction.unitCount()).isEqualTo(3);
        assertThat(extraction.blocks()).extracting(TextBlock::text).containsExactly("Mở đầu", "Mục tiêu buổi học",
                "Bảng số liệu", "Năm | Dân số\n2020 | 97 triệu", "Tổng kết", "Ôn lại ba ý chính");
        assertThat(extraction.blocks()).extracting(block -> block.location().number())
                .containsExactly(1, 1, 2, 2, 3, 3);
        assertThat(extraction.blocks()).extracting(TextBlock::role).containsExactly(Role.HEADING, Role.BODY,
                Role.HEADING, Role.TABLE, Role.HEADING, Role.BODY);
        assertThat(extraction.blocks()).allMatch(block -> block.location().kind() == Kind.SLIDE);
    }

    @Test
    @DisplayName("Sách điện tử: chương theo thứ tự đọc, đề mục h1–h6, ký tự đặc biệt được giải mã, bảng giữ cấu trúc")
    void epubFollowsSpine() {
        Extraction extraction = new EpubTextExtractor().extract(ExtractorSamples.epub(), "", recognizer);

        assertThat(extraction.blocks()).extracting(TextBlock::text).containsExactly("Lời nói đầu",
                "Cuốn sách dành cho học sinh lớp 12 & người tự học.", "Chương một", "Đoạn thứ nhất.",
                "Đoạn thứ hai.", "A | B\n1 | 2");
        assertThat(extraction.blocks()).extracting(TextBlock::role).containsExactly(Role.HEADING, Role.BODY,
                Role.HEADING, Role.BODY, Role.BODY, Role.TABLE);
        assertThat(extraction.blocks()).extracting(block -> block.location().number())
                .containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("Văn bản thuần và Markdown: đề mục, bảng, công thức, mã; văn bản không có dòng trống thì mỗi dòng một đoạn")
    void plainTextAndMarkdown() {
        String markdown = """
                # Ôn tập

                Câu hỏi thường gặp.

                | Khái niệm | Ý nghĩa |
                |---|---|
                | Đạo hàm | Tốc độ thay đổi |

                $$
                \\int_0^1 x\\,dx = \\frac{1}{2}
                $$

                ```
                print("xin chào")
                ```
                """;
        Extraction extraction = new PlainTextExtractor().extract(markdown.getBytes(StandardCharsets.UTF_8),
                "text/markdown", recognizer);
        assertThat(extraction.blocks()).extracting(TextBlock::role).containsExactly(Role.HEADING, Role.BODY,
                Role.TABLE, Role.FORMULA, Role.CODE);
        assertThat(extraction.blocks().get(2).text()).isEqualTo("Khái niệm | Ý nghĩa\nĐạo hàm | Tốc độ thay đổi");
        assertThat(extraction.blocks().get(3).text()).contains("\\frac{1}{2}").startsWith("$$");

        Extraction lines = new PlainTextExtractor().extract("Dòng một\nDòng hai\nDòng ba".getBytes(
                StandardCharsets.UTF_8), "text/plain", recognizer);
        assertThat(lines.blocks()).extracting(block -> block.location().number()).containsExactly(1, 2, 3);

        assertThatThrownBy(() -> new PlainTextExtractor().extract(new byte[] {(byte) 0xff, (byte) 0xfe, 0x41},
                "text/plain", recognizer)).isInstanceOf(UnreadableDocumentException.class);
    }

    @Test
    @DisplayName("Ảnh: toàn bộ chữ đi qua nhận dạng chữ, mọi khối trỏ về chính ảnh đó")
    void imagesGoThroughOcr() {
        Extraction extraction = new ImageTextExtractor().extract(SampleDocuments.notebook("Ghi chép"), "image/png",
                (number, image, mimeType) -> "# Hóa học\n\nPhản ứng oxi hóa khử.\n\nCân bằng electron.");
        assertThat(extraction.blocks()).extracting(TextBlock::location)
                .containsOnly(ChunkLocation.of(Kind.IMAGE, 1));
        assertThat(extraction.blocks()).extracting(TextBlock::source).containsOnly(DocumentChunk.Source.OCR);
        assertThat(extraction.blocks()).extracting(TextBlock::role).containsExactly(Role.HEADING, Role.BODY,
                Role.BODY);
    }

    @Test
    @DisplayName("Trang web: bỏ script và style, giữ đề mục và đoạn văn")
    void webPages() {
        String html = "<html><head><style>p{}</style><script>alert('x')</script></head><body>"
                + "<h2>Quang hợp</h2><p>Cây xanh <b>hấp thụ</b> ánh sáng.</p><ul><li>Diệp lục</li></ul></body></html>";
        Extraction extraction = new HtmlTextExtractor().extract(html.getBytes(StandardCharsets.UTF_8), "text/html",
                recognizer);
        assertThat(extraction.blocks()).extracting(TextBlock::text).containsExactly("Quang hợp",
                "Cây xanh hấp thụ ánh sáng.", "Diệp lục");
    }
}
