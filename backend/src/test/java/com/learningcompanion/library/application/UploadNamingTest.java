package com.learningcompanion.library.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.learningcompanion.library.domain.DocumentType;
import com.learningcompanion.shared.storage.FileTypeDetector.FileKind;
import org.junit.jupiter.api.Test;

/** Tên tệp do trình duyệt gửi được làm sạch; tên tài liệu mặc định là tên tệp bỏ đuôi. */
class UploadNamingTest {

    @Test
    void cleansFileNames() {
        assertThat(UploadService.cleanFileName("C:\\Users\\lan\\Giáo trình chương 1.pdf"))
                .isEqualTo("Giáo trình chương 1.pdf");
        assertThat(UploadService.cleanFileName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(UploadService.cleanFileName("  bài\u0000 giảng  .pdf ")).isEqualTo("bài giảng .pdf");
        assertThat(UploadService.cleanFileName(null)).isEmpty();
        assertThat(UploadService.cleanFileName("a".repeat(400) + ".pdf")).hasSize(UploadService.FILE_NAME_MAX_LENGTH);
    }

    @Test
    void titleDropsExtension() {
        assertThat(UploadService.titleOf("Giáo trình chương 1.pdf")).isEqualTo("Giáo trình chương 1");
        assertThat(UploadService.titleOf(".bashrc")).isEqualTo(".bashrc");
        assertThat(UploadService.titleOf("ghi-chu")).isEqualTo("ghi-chu");
    }

    @Test
    void mapsDetectedKindToDocumentType() {
        assertThat(UploadService.typeOf(FileKind.WEBP)).isEqualTo(DocumentType.IMAGE);
        assertThat(UploadService.typeOf(FileKind.EPUB)).isEqualTo(DocumentType.EPUB);
        assertThat(UploadService.mimeTypeOf(FileKind.TEXT, "Ôn tập.MD")).isEqualTo("text/markdown; charset=utf-8");
        assertThat(UploadService.mimeTypeOf(FileKind.PDF, "a.pdf")).isEqualTo("application/pdf");
    }
}
