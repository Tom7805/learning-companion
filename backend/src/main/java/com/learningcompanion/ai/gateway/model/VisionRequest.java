package com.learningcompanion.ai.gateway.model;

import java.util.Objects;

/** Một hình ảnh (PNG, JPEG, WebP) và câu hỏi về hình; nhận dạng chữ bỏ qua câu hỏi. */
public record VisionRequest(byte[] image, String mimeType, String question) {

    public VisionRequest {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(mimeType, "mimeType");
        if (image.length == 0) {
            throw new IllegalArgumentException("Hình ảnh rỗng");
        }
    }
}
