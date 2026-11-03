package com.learningcompanion.ai.gateway.api.dto;

import com.learningcompanion.ai.gateway.AiCapability;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Nhà cung cấp như quản trị viên thấy; chỉ cho biết kho bí mật đã có khóa hay chưa, không bao giờ trả giá trị khóa. */
public record AiProviderResponse(UUID id, String code, String displayName, String adapter, String baseUrl,
                                 String textModel, String embeddingModel, String visionModel,
                                 List<AiCapability> capabilities, List<AiCapability> adapterCapabilities,
                                 String secretName, boolean secretRequired, boolean secretConfigured,
                                 BigDecimal inputPricePerMillion, BigDecimal outputPricePerMillion,
                                 boolean noTrainingConfirmed, Instant noTrainingConfirmedAt, boolean enabled,
                                 List<AiCapability> primaryFor, List<AiCapability> fallbackFor, Instant updatedAt) {
}
