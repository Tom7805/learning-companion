package com.learningcompanion.shared.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Component;

/**
 * Nhận dạng tệp tải lên theo nội dung thật, không tin đuôi tệp hay kiểu do trình duyệt khai (QTN-19): đọc chữ ký đầu
 * tệp, mở thử cấu trúc bên trong để biết tệp có mật khẩu hay bị hỏng, và đếm số byte bung ra của tệp nén để chặn tệp
 * nén bung ra quá lớn.
 */
@Component
public class FileTypeDetector {

    private static final byte[] PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP = {'P', 'K', 3, 4};
    private static final byte[] OLE2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A,
            (byte) 0xE1};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = "WEBP".getBytes(StandardCharsets.US_ASCII);
    // Tệp văn bản soạn thảo hay trình chiếu đặt mật khẩu được bọc trong vỏ OLE2 kèm luồng mô tả cách mã hóa.
    private static final byte[] ENCRYPTION_INFO = "EncryptionInfo".getBytes(StandardCharsets.UTF_16LE);
    private static final int MAX_ARCHIVE_ENTRIES = 10_000;
    private static final long MAX_IMAGE_PIXELS = 200_000_000L;

    private final long maxInflatedBytes;
    private final int maxInflateRatio;

    public FileTypeDetector(UploadProperties properties) {
        this.maxInflatedBytes = properties.maxInflatedBytes();
        this.maxInflateRatio = properties.maxInflateRatio();
    }

    public Inspection inspect(byte[] content) {
        if (content.length == 0) {
            return Inspection.problem(Problem.EMPTY);
        }
        // Chữ ký tệp dạng trang được phép lùi sau vài byte rác ở đầu, như các trình đọc vẫn chấp nhận.
        if (indexOf(content, PDF, 1024) >= 0) {
            return inspectPdf(content);
        }
        if (startsWith(content, ZIP, 0)) {
            return inspectArchive(content);
        }
        if (startsWith(content, OLE2, 0)) {
            // Tệp Word, PowerPoint đời cũ không được nhận; tệp đời mới có mật khẩu cũng nằm trong vỏ này.
            return Inspection.problem(indexOf(content, ENCRYPTION_INFO, content.length) >= 0
                    ? Problem.PASSWORD_PROTECTED : Problem.UNSUPPORTED);
        }
        if (startsWith(content, PNG, 0)) {
            return inspectImage(content, FileKind.PNG);
        }
        if (startsWith(content, JPEG, 0)) {
            return inspectImage(content, FileKind.JPEG);
        }
        if (startsWith(content, RIFF, 0) && startsWith(content, WEBP, 8)) {
            // Thư viện ảnh của Java không đọc được WebP: chỉ kiểm phần đầu khai đúng độ dài tệp.
            long declared = (Byte.toUnsignedLong(content[4]) | Byte.toUnsignedLong(content[5]) << 8
                    | Byte.toUnsignedLong(content[6]) << 16 | Byte.toUnsignedLong(content[7]) << 24) + 8;
            return declared > content.length ? Inspection.problem(Problem.CORRUPTED) : Inspection.ok(FileKind.WEBP);
        }
        return isUtf8Text(content) ? Inspection.ok(FileKind.TEXT) : Inspection.problem(Problem.UNSUPPORTED);
    }

    private static Inspection inspectPdf(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            return document.getNumberOfPages() > 0 ? Inspection.ok(FileKind.PDF)
                    : Inspection.problem(Problem.CORRUPTED);
        } catch (InvalidPasswordException ex) {
            return Inspection.problem(Problem.PASSWORD_PROTECTED);
        } catch (IOException | RuntimeException ex) {
            return Inspection.problem(Problem.CORRUPTED);
        }
    }

    private Inspection inspectArchive(byte[] content) {
        long limit = Math.min(maxInflatedBytes, (long) content.length * maxInflateRatio);
        Set<String> names = new HashSet<>();
        String epubMimetype = null;
        long inflated = 0;
        byte[] buffer = new byte[16 * 1024];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (names.size() >= MAX_ARCHIVE_ENTRIES) {
                    return Inspection.problem(Problem.EXPANDS_TOO_LARGE);
                }
                names.add(entry.getName());
                boolean mimetype = "mimetype".equals(entry.getName());
                StringBuilder mimetypeText = mimetype ? new StringBuilder() : null;
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    inflated += read;
                    if (inflated > limit) {
                        return Inspection.problem(Problem.EXPANDS_TOO_LARGE);
                    }
                    if (mimetypeText != null && mimetypeText.length() < 100) {
                        mimetypeText.append(new String(buffer, 0, read, StandardCharsets.US_ASCII));
                    }
                }
                if (mimetypeText != null) {
                    epubMimetype = mimetypeText.toString().strip();
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            return Inspection.problem(Problem.CORRUPTED);
        }
        if (names.isEmpty()) {
            return Inspection.problem(Problem.CORRUPTED);
        }
        if ("application/epub+zip".equals(epubMimetype)) {
            return names.contains("META-INF/container.xml") ? Inspection.ok(FileKind.EPUB)
                    : Inspection.problem(Problem.CORRUPTED);
        }
        if (names.contains("[Content_Types].xml")) {
            if (names.contains("word/document.xml")) {
                return Inspection.ok(FileKind.DOCX);
            }
            if (names.contains("ppt/presentation.xml")) {
                return Inspection.ok(FileKind.PPTX);
            }
            boolean wordOrSlides = names.stream().anyMatch(name -> name.startsWith("word/") || name.startsWith("ppt/"));
            return Inspection.problem(wordOrSlides ? Problem.CORRUPTED : Problem.UNSUPPORTED);
        }
        return Inspection.problem(Problem.UNSUPPORTED);
    }

    private static Inspection inspectImage(byte[] content, FileKind kind) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return Inspection.problem(Problem.CORRUPTED);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0) {
                    return Inspection.problem(Problem.CORRUPTED);
                }
                return pixels > MAX_IMAGE_PIXELS ? Inspection.problem(Problem.EXPANDS_TOO_LARGE) : Inspection.ok(kind);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            return Inspection.problem(Problem.CORRUPTED);
        }
    }

    /** Văn bản thuần: mã hóa UTF-8 hợp lệ và không có byte rỗng (dấu hiệu của tệp nhị phân). */
    private static boolean isUtf8Text(byte[] content) {
        for (byte value : content) {
            if (value == 0) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            return true;
        } catch (CharacterCodingException ex) {
            return false;
        }
    }

    private static boolean startsWith(byte[] content, byte[] prefix, int offset) {
        if (content.length < offset + prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static int indexOf(byte[] content, byte[] needle, int within) {
        int end = Math.min(content.length, within) - needle.length;
        for (int i = 0; i <= end; i++) {
            if (startsWith(content, needle, i)) {
                return i;
            }
        }
        return -1;
    }

    /** Loại tệp được nhận, kèm kiểu nội dung chuẩn để lưu và trả cho trình duyệt. */
    public enum FileKind {
        PDF("application/pdf"),
        DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation"),
        EPUB("application/epub+zip"),
        TEXT("text/plain; charset=utf-8"),
        JPEG("image/jpeg"),
        PNG("image/png"),
        WEBP("image/webp");

        private final String mimeType;

        FileKind(String mimeType) {
            this.mimeType = mimeType;
        }

        public String mimeType() {
            return mimeType;
        }
    }

    /** Lý do tệp không được nhận. */
    public enum Problem {
        EMPTY,
        UNSUPPORTED,
        PASSWORD_PROTECTED,
        CORRUPTED,
        EXPANDS_TOO_LARGE
    }

    public record Inspection(FileKind kind, Problem problem) {

        static Inspection ok(FileKind kind) {
            return new Inspection(kind, null);
        }

        static Inspection problem(Problem problem) {
            return new Inspection(null, problem);
        }

        public boolean accepted() {
            return problem == null;
        }
    }
}
