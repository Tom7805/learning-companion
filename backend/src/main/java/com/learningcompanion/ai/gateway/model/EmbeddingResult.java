package com.learningcompanion.ai.gateway.model;

import java.util.List;

/** Mỗi đoạn văn bản một vector, cùng thứ tự với yêu cầu. */
public record EmbeddingResult(List<float[]> vectors, AiUsage usage, String providerCode) {

    public EmbeddingResult withProvider(String code) {
        return new EmbeddingResult(vectors, usage, code);
    }
}
