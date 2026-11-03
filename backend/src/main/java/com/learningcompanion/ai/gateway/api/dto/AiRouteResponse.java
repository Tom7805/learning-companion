package com.learningcompanion.ai.gateway.api.dto;

import com.learningcompanion.ai.gateway.AiCapability;
import java.time.Instant;
import java.util.UUID;

/** Định tuyến một khả năng kèm trạng thái mạch hiện tại của nhà cung cấp chính và dự phòng. */
public record AiRouteResponse(AiCapability capability, Endpoint primary, Endpoint fallback, int timeoutSeconds,
                              int maxRetries, int breakerFailureThreshold, int breakerWindowSeconds,
                              int breakerOpenSeconds, Instant updatedAt) {

    /** Một đầu của định tuyến: nhà cung cấp và mạch của nó cho khả năng này. */
    public record Endpoint(UUID id, String code, String displayName, boolean enabled, String circuit,
                           int recentFailures, Instant openedAt, Instant retryAt) {
    }
}
