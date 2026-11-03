package com.learningcompanion.ai.provider;

/**
 * Thông tin bộ chuyển đổi cần để gọi một nhà cung cấp, kèm khóa bí mật đã đọc từ kho bí mật ngay trước lời gọi.
 * {@link #toString()} không bao giờ in khóa.
 */
public record ProviderDescriptor(String code, AdapterType adapter, String baseUrl, String textModel,
                                 String embeddingModel, String visionModel, String secret) {

    @Override
    public String toString() {
        return "ProviderDescriptor[code=" + code + ", adapter=" + adapter + "]";
    }
}
