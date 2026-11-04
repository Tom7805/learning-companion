package com.learningcompanion.library.infrastructure.extractor;

import java.awt.image.BufferedImage;

/** Tiện ích ảnh cho trích văn bản. */
final class Images {

    /** Chênh lệch độ sáng tối thiểu giữa điểm sáng nhất và tối nhất để coi trang có nội dung. */
    private static final int MIN_CONTRAST = 24;
    /** Tỉ lệ điểm tối tối thiểu (một phần mười nghìn): vài chấm bụi trên trang quét không tính là chữ. */
    private static final int MIN_INK_PER_10K = 2;

    private Images() {
    }

    /** Trang trắng (hoặc một màu): không cần gọi nhận dạng chữ. */
    static boolean isBlank(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int stepX = Math.max(1, width / 400);
        int stepY = Math.max(1, height / 400);
        int min = 255;
        int max = 0;
        long samples = 0;
        int[] luminance = new int[(width / stepX + 1) * (height / stepY + 1)];
        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                int rgb = image.getRGB(x, y);
                int value = (((rgb >> 16) & 0xff) * 299 + ((rgb >> 8) & 0xff) * 587 + (rgb & 0xff) * 114) / 1000;
                luminance[(int) samples++] = value;
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }
        if (max - min < MIN_CONTRAST) {
            return true;
        }
        int threshold = (min + max) / 2;
        long ink = 0;
        for (int i = 0; i < samples; i++) {
            if (luminance[i] < threshold) {
                ink++;
            }
        }
        return ink * 10_000 < samples * MIN_INK_PER_10K;
    }
}
