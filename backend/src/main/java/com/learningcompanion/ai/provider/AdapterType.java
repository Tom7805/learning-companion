package com.learningcompanion.ai.provider;

import com.learningcompanion.ai.gateway.AiCapability;
import java.util.EnumSet;
import java.util.Set;

/** Cách nói chuyện với một nhà cung cấp. Đổi nhà cung cấp là thêm một dòng cấu hình dùng một trong các kiểu này. */
public enum AdapterType {
    /** Bản mô phỏng trả kết quả cố định, cho môi trường thử và máy phát triển. */
    MOCK(EnumSet.allOf(AiCapability.class), false),
    /** Mọi dịch vụ theo giao diện kiểu OpenAI (chat completions, embeddings). */
    OPENAI_COMPATIBLE(EnumSet.allOf(AiCapability.class), true),
    /** Giao diện Messages của Anthropic; không có biểu diễn ngữ nghĩa. */
    ANTHROPIC(EnumSet.of(AiCapability.TEXT_GENERATION, AiCapability.VISION, AiCapability.OCR), true);

    private final Set<AiCapability> supported;
    private final boolean requiresSecret;

    AdapterType(Set<AiCapability> supported, boolean requiresSecret) {
        this.supported = supported;
        this.requiresSecret = requiresSecret;
    }

    public boolean supports(AiCapability capability) {
        return supported.contains(capability);
    }

    public Set<AiCapability> supported() {
        return EnumSet.copyOf(supported);
    }

    public boolean requiresSecret() {
        return requiresSecret;
    }
}
