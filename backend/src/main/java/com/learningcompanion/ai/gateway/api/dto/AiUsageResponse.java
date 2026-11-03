package com.learningcompanion.ai.gateway.api.dto;

import com.learningcompanion.ai.gateway.AiCapability;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Mức sử dụng trong một khoảng ngày: tổng theo khả năng và nhà cung cấp, và các lời gọi gần nhất (không nội dung). */
public record AiUsageResponse(Instant since, Totals totals, List<Row> rows, List<Call> recentCalls) {

    public record Totals(long calls, long failures, long inputUnits, long outputUnits, long costMicroUsd) {
    }

    public record Row(AiCapability capability, String providerCode, long calls, long failures, long fallbackCalls,
                      long inputUnits, long outputUnits, long costMicroUsd, long averageLatencyMs) {
    }

    public record Call(UUID id, Instant occurredAt, AiCapability capability, String feature, String providerCode,
                       String outcome, boolean usedFallback, int attempts, int inputUnits, int outputUnits,
                       int latencyMs, long costMicroUsd, String errorCode) {
    }
}
