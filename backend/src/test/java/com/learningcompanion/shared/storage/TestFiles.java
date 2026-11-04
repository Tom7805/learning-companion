package com.learningcompanion.shared.storage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

/** Tệp mẫu dựng trong bộ nhớ cho kiểm thử tải lên: tài liệu dạng trang, tệp nén văn phòng, ảnh, tệp mã độc thử. */
public final class TestFiles {

    /** Tệp thử mã độc chuẩn EICAR, vô hại nhưng mọi bộ quét đều coi là mã độc. */
    public static final String EICAR =
            "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    private TestFiles() {
    }

    /** Tài liệu dạng trang hợp lệ; {@code marker} khác nhau cho ra nội dung (và dấu vân tay) khác nhau. */
    public static byte[] pdf(String marker) {
        return pdf(marker, null);
    }

    public static byte[] pdf(String marker, String userPassword) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.getDocumentInformation().setTitle(marker);
            // Đệm thêm để tệp mẫu dài hơn một phần tải lên trong kiểm thử tích hợp.
            document.getDocumentInformation().setSubject("Bài giảng mẫu ".repeat(120));
            if (userPassword != null) {
                StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-" + userPassword, userPassword,
                        new AccessPermission());
                policy.setEncryptionKeyLength(128);
                document.protect(policy);
            }
            document.save(out, CompressParameters.NO_COMPRESSION);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static byte[] zip(Map<String, byte[]> entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return out.toByteArray();
    }

    public static byte[] docx() {
        return zip(Map.of("[Content_Types].xml", xml("<Types/>"), "word/document.xml", xml("<w:document/>")));
    }

    public static byte[] pptx() {
        return zip(Map.of("[Content_Types].xml", xml("<Types/>"), "ppt/presentation.xml", xml("<p:presentation/>")));
    }

    public static byte[] epub() {
        return zip(new java.util.LinkedHashMap<>(Map.of("mimetype", ascii("application/epub+zip"),
                "META-INF/container.xml", xml("<container/>"))));
    }

    public static byte[] png(int width, int height) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] xml(String body) {
        return ("<?xml version=\"1.0\"?>" + body).getBytes(StandardCharsets.UTF_8);
    }
}
