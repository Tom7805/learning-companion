package com.learningcompanion.ai.gateway.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Định tuyến một khả năng. Giới hạn giữ cho lời gọi không treo quá hai phút và ngắt mạch có ý nghĩa. */
public record AiRouteRequest(
        @NotNull UUID primaryProviderId,
        UUID fallbackProviderId,
        @Min(1) @Max(120) int timeoutSeconds,
        @Min(0) @Max(3) int maxRetries,
        @Min(1) @Max(50) int breakerFailureThreshold,
        @Min(10) @Max(3600) int breakerWindowSeconds,
        @Min(5) @Max(3600) int breakerOpenSeconds) {
}
