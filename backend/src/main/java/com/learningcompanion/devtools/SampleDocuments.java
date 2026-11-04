package com.learningcompanion.devtools;

import com.learningcompanion.ai.provider.mock.MockAiAdapter;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Tệp mẫu cho môi trường thử của chức năng xử lý tài liệu (NCL-05-CN-003): bài giảng nhiều trang có thể kèm một trang
 * quét, tệp chỉ có trang trắng, ảnh chụp trang vở. Dùng cho kiểm thử đầu cuối và kiểm thử tích hợp.
 */
public final class SampleDocuments {

    private SampleDocuments() {
    }

    /**
     * Bài giảng {@code pages} trang có lớp chữ, mỗi trang mở đầu bằng đề mục "Chuong n"; trang {@code scannedPage} (0:
     * không có) là ảnh quét không có lớp chữ, phải qua nhận dạng chữ.
     */
    public static byte[] lecture(int pages, int scannedPage) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            for (int page = 1; page <= pages; page++) {
                PDPage pdfPage = new PDPage(PDRectangle.A4);
                document.addPage(pdfPage);
                try (PDPageContentStream content = new PDPageContentStream(document, pdfPage)) {
                    if (page == scannedPage) {
                        PDImageXObject image = LosslessFactory.createFromImage(document,
                                handwriting(1000, 1400, "Trang quet " + page));
                        content.drawImage(image, 40, 40, PDRectangle.A4.getWidth() - 80,
                                PDRectangle.A4.getHeight() - 80);
                        continue;
                    }
                    content.beginText();
                    content.setFont(bold, 18);
                    content.newLineAtOffset(60, 770);
                    content.showText("Chuong " + page);
                    content.setFont(font, 11);
                    content.newLineAtOffset(0, -32);
                    for (String line : lines(page)) {
                        content.showText(line);
                        content.newLineAtOffset(0, -16);
                    }
                    content.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** Tài liệu dạng trang chỉ có trang trắng. */
    public static byte[] blank(int pages) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int page = 0; page < pages; page++) {
                document.addPage(new PDPage(PDRectangle.A4));
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Ảnh chụp một trang vở viết tay. Bản mô phỏng nhận dạng chữ đọc {@code text} gắn trong ảnh (khối iTXt), nên mỗi ảnh
     * mẫu cho ra đúng chữ của nó.
     */
    public static byte[] notebook(String text) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(handwriting(640, 860, text.lines().findFirst().orElse("")), "png", out);
            return withText(out.toByteArray(), MockAiAdapter.OCR_TEXT_KEY, text);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String[] lines(int page) {
        String[] lines = new String[12];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = "Muc " + page + "." + (i + 1) + ": gioi han cua ham so tai diem x0 bang L khi moi day xn tien"
                    + " ve x0 deu cho f(xn) tien ve L.";
        }
        return lines;
    }

    /** Trang giấy có dòng kẻ và vài dòng "chữ viết tay" nét đậm. */
    private static BufferedImage handwriting(int width, int height, String caption) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(200, 215, 240));
        for (int y = 80; y < height; y += 40) {
            g.drawLine(0, y, width, y);
        }
        g.setColor(new Color(20, 30, 90));
        g.setStroke(new BasicStroke(3f));
        g.setFont(new Font(Font.SERIF, Font.ITALIC, 30));
        g.drawString(caption, 40, 70);
        for (int row = 0; row < 14; row++) {
            int y = 115 + row * 40;
            int x = 40;
            while (x < width - 80) {
                int w = 20 + (x * 7 + row * 13) % 60;
                g.drawArc(x, y - 18, w, 18, 0, 180 + (row * 31 + x) % 140);
                x += w + 14;
            }
        }
        g.dispose();
        return image;
    }

    /** Gắn một khối chữ quốc tế (iTXt, UTF-8, không nén) vào ảnh PNG, ngay trước khối kết thúc. */
    static byte[] withText(byte[] png, String keyword, String text) {
        byte[] key = keyword.getBytes(StandardCharsets.ISO_8859_1);
        byte[] value = text.getBytes(StandardCharsets.UTF_8);
        ByteBuffer data = ByteBuffer.allocate(key.length + 5 + value.length);
        data.put(key).put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0).put(value);
        byte[] type = "iTXt".getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(type);
        crc.update(data.array());
        ByteBuffer chunk = ByteBuffer.allocate(12 + data.capacity());
        chunk.putInt(data.capacity()).put(type).put(data.array()).putInt((int) crc.getValue());
        int iend = png.length - 12;
        ByteBuffer result = ByteBuffer.allocate(png.length + chunk.capacity());
        result.put(png, 0, iend).put(chunk.array()).put(png, iend, 12);
        return result.array();
    }
}
