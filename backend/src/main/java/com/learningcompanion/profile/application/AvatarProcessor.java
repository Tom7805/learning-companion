package com.learningcompanion.profile.application;

import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;

/**
 * Kiểm và chuẩn hóa ảnh đại diện: chỉ nhận ảnh JPG hoặc PNG giải mã được thật (đổi đuôi tệp văn bản
 * không qua được), cắt vuông ở giữa, thu về 256 điểm ảnh và mã hóa lại PNG nên mọi siêu dữ liệu
 * (vị trí chụp, thiết bị) bị loại bỏ. Giao diện cắt sẵn ảnh WebP về JPG trước khi gửi.
 */
@Component
public class AvatarProcessor {

    public static final List<String> ACCEPTED_FORMATS = List.of("JPG", "PNG", "WebP");
    static final int MAX_BYTES = 5 * 1024 * 1024;
    static final int OUTPUT_SIZE = 256;
    private static final int MAX_DIMENSION = 8000;

    public byte[] process(byte[] input) {
        if (input == null || input.length == 0 || !(isJpeg(input) || isPng(input))) {
            throw invalid();
        }
        if (input.length > MAX_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "avatar", Map.of("maxBytes", MAX_BYTES));
        }
        BufferedImage image = decode(input);
        int side = Math.min(image.getWidth(), image.getHeight());
        BufferedImage square = image.getSubimage((image.getWidth() - side) / 2, (image.getHeight() - side) / 2,
                side, side);
        BufferedImage output = new BufferedImage(OUTPUT_SIZE, OUTPUT_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(square, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE, null);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            ImageIO.write(output, "png", bytes);
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Không mã hóa được ảnh đại diện", ex);
        }
    }

    /** Đọc kích thước trước khi giải mã để chặn ảnh "bom" kích thước khổng lồ. */
    private static BufferedImage decode(byte[] input) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw invalid();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                if (reader.getWidth(0) > MAX_DIMENSION || reader.getHeight(0) > MAX_DIMENSION) {
                    throw invalid();
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof BusinessException business) {
                throw business;
            }
            throw invalid();
        }
    }

    private static boolean isJpeg(byte[] bytes) {
        return bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] bytes) {
        return bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.AVATAR_INVALID_FORMAT, "avatar",
                Map.of("acceptedFormats", ACCEPTED_FORMATS));
    }
}
