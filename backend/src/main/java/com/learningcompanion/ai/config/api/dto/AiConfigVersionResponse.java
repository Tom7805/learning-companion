package com.learningcompanion.ai.config.api.dto;

import com.learningcompanion.ai.config.domain.AiFeature;
import com.learningcompanion.ai.config.domain.ConfigVersionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Một phiên bản cấu hình đầy đủ, kèm người tạo, người phát hành và người thu hồi (trống nghĩa là hệ thống). */
public record AiConfigVersionResponse(
        UUID id,
        AiFeature feature,
        int versionNumber,
        ConfigVersionStatus status,
        String providerCode,
        String model,
        BigDecimal temperature,
        int maxOutputTokens,
        String systemPrompt,
        Integer basedOnVersion,
        Instant createdAt,
        Actor createdBy,
        Instant lastTrialAt,
        Instant publishedAt,
        Actor publishedBy,
        Instant revokedAt,
        Actor revokedBy) {

    public record Actor(UUID id, String displayName) {
    }
}
