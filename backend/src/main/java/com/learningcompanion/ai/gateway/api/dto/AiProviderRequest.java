package com.learningcompanion.ai.gateway.api.dto;

import com.learningcompanion.ai.gateway.AiCapability;
import com.learningcompanion.ai.provider.AdapterType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Set;

/**
 * Khai báo hoặc sửa một nhà cung cấp. Mã chỉ dùng khi thêm mới. Khóa bí mật không bao giờ gửi qua đây: chỉ tên khóa
 * trong kho bí mật.
 */
public record AiProviderRequest(
        @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,39}") String code,
        @NotBlank @Size(max = 80) String displayName,
        @NotNull AdapterType adapter,
        @Size(max = 300) @Pattern(regexp = "https://\\S+") String baseUrl,
        @Size(max = 120) String textModel,
        @Size(max = 120) String embeddingModel,
        @Size(max = 120) String visionModel,
        @NotEmpty Set<AiCapability> capabilities,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{2,119}") String secretName,
        @NotNull @DecimalMin("0") @DecimalMax("100000") BigDecimal inputPricePerMillion,
        @NotNull @DecimalMin("0") @DecimalMax("100000") BigDecimal outputPricePerMillion,
        boolean noTrainingConfirmed) {
}
