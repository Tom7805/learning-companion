package com.learningcompanion.ai.gateway.model;

/** Kết quả hoàn chỉnh của một lời gọi sinh văn bản hoặc đọc hình ảnh, kèm nhà cung cấp đã trả lời. */
public record ChatResult(String text, AiUsage usage, String providerCode) {

    public ChatResult withProvider(String code) {
        return new ChatResult(text, usage, code);
    }
}
