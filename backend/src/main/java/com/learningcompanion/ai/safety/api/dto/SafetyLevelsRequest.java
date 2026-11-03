package com.learningcompanion.ai.safety.api.dto;

import com.learningcompanion.ai.safety.domain.ModerationCategory;
import com.learningcompanion.ai.safety.domain.SafetyLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Ngưỡng lọc mới: chỉ cần gửi các nhóm muốn đổi. */
public record SafetyLevelsRequest(@NotEmpty @Valid List<Entry> levels) {

    public record Entry(@NotNull ModerationCategory category, @NotNull SafetyLevel level) {
    }
}
