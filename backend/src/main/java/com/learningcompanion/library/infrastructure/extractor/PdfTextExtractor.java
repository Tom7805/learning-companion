package com.learningcompanion.library.infrastructure.extractor;

import com.learningcompanion.library.domain.ChunkLocation;
import com.learningcompanion.library.domain.DocumentChunk;
import com.learningcompanion.library.domain.DocumentType;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Tài liệu dạng trang: lớp chữ của từng trang được trích trực tiếp; trang không có lớp chữ (trang quét) được dựng thành
 * ảnh rồi đi qua nhận dạng chữ, trừ khi trang trắng. Mỗi khối gắn đúng số trang (TC-01).
 */
@Component
public class PdfTextExtractor implements TextExtractor {

    /** Độ phân giải khi dựng trang quét thành ảnh cho nhận dạng chữ: đủ rõ chữ in thường, ảnh không quá nặng. */
    static final float OCR_DPI = 150f;

    @Override
    public Set<DocumentType> types() {
        return Set.of(DocumentType.PDF);
    }

    @Override
    public Extraction extract(byte[] file, String mimeType, PageRecognizer recognizer) {
        try (PDDocument pdf = Loader.loadPDF(file)) {
            if (pdf.isEncrypted()) {
                throw new UnreadableDocumentException("Tài liệu có mật khẩu");
            }
            int pages = pdf.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setParagraphEnd("\n\n");
            stripper.setPageEnd("");
            PDFRenderer renderer = null;
            List<TextBlock> blocks = new ArrayList<>();
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(pdf).strip();
                ChunkLocation location = ChunkLocation.of(ChunkLocation.Kind.PAGE, page);
                if (!text.isEmpty()) {
                    blocks.addAll(Paragraphs.split(text, location, DocumentChunk.Source.TEXT));
                    continue;
                }
                if (renderer == null) {
                    renderer = new PDFRenderer(pdf);
                }
                BufferedImage image = renderer.renderImageWithDPI(page - 1, OCR_DPI, ImageType.GRAY);
                if (Images.isBlank(image)) {
                    continue;
                }
                String recognized = recognizer.recognize(page, png(image), "image/png");
                blocks.addAll(Paragraphs.split(recognized, location, DocumentChunk.Source.OCR));
            }
            return new Extraction(blocks, pages);
        } catch (IOException ex) {
            throw new UnreadableDocumentException("Không đọc được tài liệu dạng trang", ex);
        }
    }

    private static byte[] png(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
