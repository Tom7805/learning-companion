package com.learningcompanion.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.learningcompanion.profile.application.AvatarProcessor;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class AvatarProcessorTest {

    private final AvatarProcessor processor = new AvatarProcessor();

    @Test
    void wideJpegIsCroppedToCentredSquareAndReencodedAsPng() throws Exception {
        BufferedImage wide = new BufferedImage(600, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = wide.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 150, 300);
        graphics.setColor(Color.BLUE);
        graphics.fillRect(150, 0, 300, 300);
        graphics.dispose();

        byte[] output = processor.process(encode(wide, "jpg"));

        BufferedImage result = ImageIO.read(new ByteArrayInputStream(output));
        assertThat(output[1]).isEqualTo((byte) 'P');
        assertThat(result.getWidth()).isEqualTo(256);
        assertThat(result.getHeight()).isEqualTo(256);
        // Phần đỏ hai bên bị cắt bỏ, ở giữa toàn màu xanh.
        Color corner = new Color(result.getRGB(2, 2));
        assertThat(corner.getBlue()).isGreaterThan(200);
        assertThat(corner.getRed()).isLessThan(60);
    }

    @Test
    void renamedTextFileIsRejectedWithAcceptedFormats() {
        byte[] text = "Đây không phải ảnh, chỉ là tệp văn bản đổi đuôi .png".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> processor.process(text))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException business = (BusinessException) ex;
                    assertThat(business.code()).isEqualTo(ErrorCode.AVATAR_INVALID_FORMAT);
                    assertThat(business.details()).containsEntry("acceptedFormats", List.of("JPG", "PNG", "WebP"));
                });
    }

    @Test
    void fakePngHeaderWithGarbageIsRejected() {
        byte[] fake = new byte[64];
        System.arraycopy(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, 0, fake, 0, 8);

        assertThatThrownBy(() -> processor.process(fake))
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(ErrorCode.AVATAR_INVALID_FORMAT));
    }

    @Test
    void emptyUploadIsRejected() {
        assertThatThrownBy(() -> processor.process(new byte[0]))
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(ErrorCode.AVATAR_INVALID_FORMAT));
    }

    static byte[] encode(BufferedImage image, String format) throws Exception {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, bytes);
            return bytes.toByteArray();
        }
    }
}
