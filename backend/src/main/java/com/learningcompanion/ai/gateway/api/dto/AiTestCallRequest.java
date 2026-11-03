package com.learningcompanion.ai.gateway.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Lời gọi thử sinh văn bản từ trang quản trị, đi qua lớp kết nối như mọi chức năng khác. */
public record AiTestCallRequest(@NotBlank @Size(max = 2000) String prompt) {
}
