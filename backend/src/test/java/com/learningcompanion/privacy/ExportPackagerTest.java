package com.learningcompanion.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.learningcompanion.privacy.application.ExportPackager;
import com.learningcompanion.shared.export.DataExportContributor;
import com.learningcompanion.shared.export.DataExportRegistry;
import com.learningcompanion.shared.export.ExportFormat;
import com.learningcompanion.shared.export.ExportWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Bộ dựng gói xuất: cấu trúc gói, định dạng mở và làm sạch tên tệp lấy từ dữ liệu người học. */
class ExportPackagerTest {

    private static final UUID OWNER = UUID.fromString("7a1c1f9e-0000-4000-8000-000000000001");
    /** 23:30 giờ UTC là 06:30 sáng hôm sau ở Việt Nam: tên gói theo ngày của người học. */
    private static final Instant GENERATED_AT = Instant.parse("2026-10-12T23:30:00Z");
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    @Test
    @DisplayName("Chức năng mới chỉ cần thêm một contributor là có mặt trong gói, kèm mô tả trong tệp hướng dẫn đọc")
    void newContributorIsIncludedAutomatically() throws IOException {
        DataExportContributor flashcards = contributor("flashcards", "Bộ thẻ ghi nhớ", 50,
                (owner, writer) -> writer.csv("decks/Từ vựng.csv", List.of("front", "back"),
                        List.of(List.of("apple", "quả táo"))));
        DataExportContributor notes = contributor("notes", "Ghi chú", 45,
                (owner, writer) -> writer.text("Ôn tập.md", "# Ôn tập\n"));

        ExportPackager.Built built = packager(flashcards, notes).build(OWNER, "lan@example.com", GENERATED_AT, VIETNAM);
        Map<String, byte[]> zip = unzip(built.content());

        String root = "learning-companion-export-2026-10-13/";
        assertThat(zip.keySet()).containsExactly(root + "README.txt", root + "manifest.json",
                root + "notes/Ôn tập.md", root + "flashcards/decks/Từ vựng.csv");
        assertThat(built.fileCount()).isEqualTo(4);
        assertThat(built.sections()).extracting(section -> section.id() + ":" + section.fileCount())
                .containsExactly("notes:1", "flashcards:1");
        String readme = new String(zip.get(root + "README.txt"), StandardCharsets.UTF_8);
        assertThat(readme).contains("lan@example.com").contains("06:30 ngày 13/10/2026 (Asia/Ho_Chi_Minh)")
                .contains("Bộ thẻ ghi nhớ  (thư mục flashcards/)").contains("Định dạng: CSV")
                .contains("\r\n");
        assertThat(new String(zip.get(root + "manifest.json"), StandardCharsets.UTF_8))
                .contains("\"formatVersion\"").contains("\"flashcards/decks/Từ vựng.csv\"");
    }

    @Test
    @DisplayName("Phần chưa có dữ liệu vẫn được nêu trong tệp hướng dẫn đọc")
    void emptySectionIsDescribed() throws IOException {
        DataExportContributor quizzes = contributor("quizzes", "Bài kiểm tra", 60, (owner, writer) -> {
        });
        Map<String, byte[]> zip = unzip(packager(quizzes).build(OWNER, "lan@example.com", GENERATED_AT, VIETNAM)
                .content());
        assertThat(new String(zip.get("learning-companion-export-2026-10-13/README.txt"), StandardCharsets.UTF_8))
                .contains("Bài kiểm tra").contains("Chưa có dữ liệu ở phần này");
    }

    @Test
    @DisplayName("CSV là UTF-8 có BOM, đúng RFC 4180 và không để bảng tính chạy công thức")
    void csvIsSafeAndOpen() throws IOException {
        DataExportContributor deck = contributor("deck", "Thẻ", 10, (owner, writer) -> writer.csv("cards.csv",
                List.of("front", "back"), List.of(
                        Arrays.asList("=HYPERLINK(\"x\")", "a, b"),
                        Arrays.asList("dòng 1\ndòng 2", null),
                        Arrays.asList("-5", "- gạch đầu dòng"),
                        Arrays.asList("-cmd", 42))));
        byte[] csv = unzip(packager(deck).build(OWNER, "lan@example.com", GENERATED_AT, VIETNAM).content())
                .get("learning-companion-export-2026-10-13/deck/cards.csv");

        assertThat(csv).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8)).isEqualTo("""
                front,back\r
                "'=HYPERLINK(""x"")","a, b"\r
                "dòng 1
                dòng 2",\r
                -5,- gạch đầu dòng\r
                '-cmd,42\r
                """);
    }

    @Test
    @DisplayName("Tên lấy từ dữ liệu người học không tạo được thư mục lạ, tên trùng được đánh số")
    void namesAreSanitizedAndDeduplicated() throws IOException {
        DataExportContributor library = contributor("library", "Thư viện", 40, (owner, writer) -> {
            assertThat(writer.bytes(ExportWriter.path("files", "../../etc/passwd"), new byte[] {1}))
                    .isEqualTo("files/.._.._etc_passwd");
            assertThat(writer.bytes(ExportWriter.path("files", "Bài 1.pdf"), new byte[] {2})).isEqualTo("files/Bài 1.pdf");
            assertThat(writer.bytes(ExportWriter.path("files", "bài 1.PDF"), new byte[] {3}))
                    .isEqualTo("files/bài 1 (2).PDF");
            assertThat(writer.bytes(ExportWriter.path("files", "CON.txt"), new byte[] {4})).isEqualTo("files/_CON.txt");
            assertThat(writer.bytes(ExportWriter.path("files", ".."), new byte[] {5})).isEqualTo("files/_");
            assertThat(writer.bytes(ExportWriter.path("files", "a:b*c?.txt"), new byte[] {6}))
                    .isEqualTo("files/a_b_c_.txt");
        });
        Map<String, byte[]> zip = unzip(packager(library).build(OWNER, "lan@example.com", GENERATED_AT, VIETNAM)
                .content());
        assertThat(zip.keySet()).allMatch(name -> name.startsWith("learning-companion-export-2026-10-13/"))
                .noneMatch(name -> name.contains("/../"));
    }

    @Test
    @DisplayName("Tên rất dài được rút gọn nhưng giữ phần mở rộng")
    void longNamesKeepExtension() {
        String name = ExportWriter.segment("a".repeat(300) + ".docx");
        assertThat(name).hasSize(120).endsWith(".docx");
    }

    @Test
    @DisplayName("Hai chức năng không được dùng chung một thư mục trong gói")
    void duplicateSectionsAreRejected() {
        DataExportContributor first = contributor("notes", "Ghi chú", 1, (owner, writer) -> {
        });
        DataExportContributor second = contributor("notes", "Ghi chú khác", 2, (owner, writer) -> {
        });
        assertThatThrownBy(() -> new DataExportRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static ExportPackager packager(DataExportContributor... contributors) {
        return new ExportPackager(new DataExportRegistry(List.of(contributors)), JsonMapper.builder().build());
    }

    private static DataExportContributor contributor(String section, String title, int order,
                                                     BiConsumer<UUID, ExportWriter> body) {
        return new DataExportContributor() {
            @Override
            public String section() {
                return section;
            }

            @Override
            public String title() {
                return title;
            }

            @Override
            public List<String> describe() {
                return List.of("Mô tả của " + title);
            }

            @Override
            public List<ExportFormat> formats() {
                return List.of(ExportFormat.CSV);
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public void contribute(UUID ownerId, ExportWriter writer) {
                body.accept(ownerId, writer);
            }
        };
    }

    private static Map<String, byte[]> unzip(byte[] content) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }
}
