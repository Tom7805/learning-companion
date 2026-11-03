package com.learningcompanion.ai.gateway.api.dto;

import com.learningcompanion.ai.gateway.AiCapability;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Toàn cảnh lớp kết nối cho trang quản trị: định tuyến, nhà cung cấp và cảnh báo chưa xác nhận. */
public record AiGatewayOverviewResponse(List<AiRouteResponse> routes, List<AiProviderResponse> providers,
                                        List<Alert> alerts) {

    public record Alert(UUID id, AiCapability capability, String providerCode, String fallbackProviderCode,
                        Instant openedAt) {
    }
}
