package com.learningcompanion.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Hãy nhập mật khẩu hiện tại.") @Size(max = 256) String currentPassword,
        @NotNull(message = "Hãy nhập mật khẩu mới.") String newPassword) {
}
