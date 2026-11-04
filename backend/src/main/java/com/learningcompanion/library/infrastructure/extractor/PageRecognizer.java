package com.learningcompanion.library.infrastructure.extractor;

/** Nhận dạng chữ của một trang quét hay một ảnh; {@code number} là số trang (hay thứ tự ảnh) để báo lỗi đúng chỗ. */
@FunctionalInterface
public interface PageRecognizer {

    String recognize(int number, byte[] image, String mimeType);
}
