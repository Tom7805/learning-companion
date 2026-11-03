package com.learningcompanion.ai.config.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Nội dung một phiên bản nháp mới (NCL-04-CN-002). Nhà cung cấp và mô hình để trống thì đi theo định tuyến của lớp
 * kết nối; {@code basedOnVersion} là phiên bản quản trị viên mở ra để sửa.
 */
public record AiConfigVersionRequest(
        @Size(max = 40) String providerCode,
        @Size(max = 120) String model,
        @NotNull @DecimalMin("0.0") @DecimalMax("2.0") @Digits(integer = 1, fraction = 2) BigDecimal temperature,
        @Min(1) @Max(32000) int maxOutputTokens,
        @NotBlank @Size(max = 20000) String systemPrompt,
        Integer basedOnVersion) {
}
