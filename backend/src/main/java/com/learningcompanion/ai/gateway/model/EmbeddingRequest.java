package com.learningcompanion.ai.gateway.model;

import java.util.List;

/** Các đoạn văn bản cần biểu diễn ngữ nghĩa. */
public record EmbeddingRequest(List<String> texts) {

    public EmbeddingRequest {
        texts = List.copyOf(texts);
        if (texts.isEmpty()) {
            throw new IllegalArgumentException("Cần ít nhất một đoạn văn bản");
        }
    }
}
