package com.learningcompanion.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.learningcompanion.shared.storage.FileTypeDetector.FileKind;
import com.learningcompanion.shared.storage.FileTypeDetector.Problem;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** QTN-19: nhận dạng tệp theo nội dung thật, từ chối tệp có mật khẩu, hỏng hay bung ra quá lớn. */
class FileTypeDetectorTest {

    private final FileTypeDetector detector = new FileTypeDetector(UploadProperties.defaults());

    @Test
    @DisplayName("Nhận đúng các định dạng được hỗ trợ theo chữ ký bên trong tệp")
    void recognisesSupportedFormats() {
        assertThat(detector.inspect(TestFiles.pdf("Giải tích")).kind()).isEqualTo(FileKind.PDF);
        assertThat(detector.inspect(TestFiles.docx()).kind()).isEqualTo(FileKind.DOCX);
        assertThat(detector.inspect(TestFiles.pptx()).kind()).isEqualTo(FileKind.PPTX);
        assertThat(detector.inspect(TestFiles.epub()).kind()).isEqualTo(FileKind.EPUB);
        assertThat(detector.inspect(TestFiles.png(4, 3)).kind()).isEqualTo(FileKind.PNG);
        assertThat(detector.inspect("Đạo hàm của hàm hợp\n".getBytes(StandardCharsets.UTF_8)).kind())
                .isEqualTo(FileKind.TEXT);
    }

    @Test
    @DisplayName("Tệp thực thi đổi đuôi thành .pdf vẫn bị nhận ra là định dạng không hỗ trợ")
    void ignoresExtensionAndRejectsBinaries() {
        byte[] executable = new byte[64];
        executable[0] = 'M';
        executable[1] = 'Z';
        assertThat(detector.inspect(executable).problem()).isEqualTo(Problem.UNSUPPORTED);
        assertThat(detector.inspect(TestFiles.zip(Map.of("a.txt", TestFiles.ascii("x")))).problem())
                .isEqualTo(Problem.UNSUPPORTED);
        assertThat(detector.inspect(new byte[] {(byte) 0xC3, (byte) 0x28}).problem()).isEqualTo(Problem.UNSUPPORTED);
    }

    @Test
    @DisplayName("Tài liệu dạng trang đặt mật khẩu mở bị từ chối vì có mật khẩu")
    void rejectsPasswordProtectedPdf() {
        assertThat(detector.inspect(TestFiles.pdf("Đề thi", "bi-mat")).problem())
                .isEqualTo(Problem.PASSWORD_PROTECTED);
    }

    @Test
    @DisplayName("Văn bản soạn thảo có mật khẩu (vỏ OLE2 chứa EncryptionInfo) bị từ chối vì có mật khẩu")
    void rejectsEncryptedOfficeFile() {
        byte[] ole = new byte[512];
        byte[] header = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        System.arraycopy(header, 0, ole, 0, header.length);
        byte[] marker = "EncryptionInfo".getBytes(StandardCharsets.UTF_16LE);
        System.arraycopy(marker, 0, ole, 200, marker.length);
        assertThat(detector.inspect(ole).problem()).isEqualTo(Problem.PASSWORD_PROTECTED);
        // Tệp Word đời cũ không mật khẩu: không hỗ trợ.
        assertThat(detector.inspect(Arrays.copyOf(header, 512)).problem()).isEqualTo(Problem.UNSUPPORTED);
    }

    @Test
    @DisplayName("Tệp hỏng: tài liệu dạng trang bị cắt cụt, tệp nén hỏng, ảnh cụt đầu đều bị từ chối vì hỏng")
    void rejectsCorruptedFiles() {
        byte[] pdf = TestFiles.pdf("Bài giảng");
        assertThat(detector.inspect(Arrays.copyOf(pdf, 40)).problem()).isEqualTo(Problem.CORRUPTED);
        byte[] docx = TestFiles.docx();
        byte[] broken = Arrays.copyOf(docx, docx.length);
        Arrays.fill(broken, 30, broken.length, (byte) 0x55);
        assertThat(detector.inspect(broken).problem()).isEqualTo(Problem.CORRUPTED);
        assertThat(detector.inspect(TestFiles.zip(Map.of("[Content_Types].xml", TestFiles.ascii("<T/>"),
                "word/styles.xml", TestFiles.ascii("<s/>")))).problem()).isEqualTo(Problem.CORRUPTED);
        assertThat(detector.inspect(Arrays.copyOf(TestFiles.png(4, 4), 12)).problem()).isEqualTo(Problem.CORRUPTED);
    }

    @Test
    @DisplayName("Tệp nén nhỏ bung ra quá lớn (bom nén) bị chặn trước khi bung hết")
    void blocksZipBombs() {
        byte[] zeros = new byte[20 * 1024 * 1024];
        byte[] bomb = TestFiles.zip(Map.of("[Content_Types].xml", TestFiles.ascii("<T/>"), "word/document.xml",
                zeros));
        assertThat(bomb.length).isLessThan(200_000);
        assertThat(detector.inspect(bomb).problem()).isEqualTo(Problem.EXPANDS_TOO_LARGE);
    }

    @Test
    void emptyFileIsRejected() {
        assertThat(detector.inspect(new byte[0]).problem()).isEqualTo(Problem.EMPTY);
    }
}
