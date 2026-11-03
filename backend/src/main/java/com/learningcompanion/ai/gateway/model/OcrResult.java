package com.learningcompanion.ai.gateway.model;

/** Chữ nhận dạng được trong hình. */
public record OcrResult(String text, AiUsage usage, String providerCode) {

    public OcrResult withProvider(String code) {
        return new OcrResult(text, usage, code);
    }
}
