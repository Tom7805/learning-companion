package com.learningcompanion.ai.safety.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Chạy thử bộ lọc: một câu hỏi, kèm (không bắt buộc) một đoạn nguồn mẫu do quản trị viên tự gõ. */
public record SafetyTrialRequest(@NotBlank @Size(max = 2000) String question, @Size(max = 4000) String source) {
}
