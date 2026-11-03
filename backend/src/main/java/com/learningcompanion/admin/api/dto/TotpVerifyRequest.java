package com.learningcompanion.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Mã sáu chữ số của ứng dụng sinh mã. */
public record TotpVerifyRequest(@NotBlank @Size(max = 16) String code) {
}
