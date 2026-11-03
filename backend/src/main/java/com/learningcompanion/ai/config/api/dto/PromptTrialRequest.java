package com.learningcompanion.ai.config.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Câu hỏi mẫu chạy thử trên một bộ nguồn mẫu với một phiên bản cấu hình. */
public record PromptTrialRequest(@NotBlank @Size(max = 1000) String question,
                                 @NotBlank @Size(max = 40) String sampleSetId) {
}
