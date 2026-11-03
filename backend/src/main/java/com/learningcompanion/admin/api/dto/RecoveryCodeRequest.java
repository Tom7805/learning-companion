package com.learningcompanion.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Một mã khôi phục dùng một lần, dùng khi mất điện thoại sinh mã. */
public record RecoveryCodeRequest(@NotBlank @Size(max = 32) String recoveryCode) {
}
