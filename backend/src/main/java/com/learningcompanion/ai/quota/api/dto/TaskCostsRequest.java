package com.learningcompanion.ai.quota.api.dto;

import com.learningcompanion.ai.quota.domain.QuotaTask;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Bảng quy đổi mới: chỉ cần gửi các loại tác vụ muốn đổi. */
public record TaskCostsRequest(@NotEmpty @Valid List<Entry> costs) {

    public record Entry(@NotNull QuotaTask task, @NotNull @Min(1) @Max(100) Integer units) {
    }
}
