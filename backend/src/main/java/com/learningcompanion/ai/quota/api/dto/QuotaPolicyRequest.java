package com.learningcompanion.ai.quota.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Hạn mức chung mới. Lượt mỗi ngày ít nhất là một để không bao giờ khóa hẳn chức năng trí tuệ nhân tạo; dung lượng
 * tính bằng mebibyte cho dễ nhập.
 */
public record QuotaPolicyRequest(
        @NotNull @Min(1) @Max(10000) Integer dailyAiUnits,
        @NotNull @Min(10) @Max(1048576) Long storageLimitMb) {
}
