package com.learningcompanion.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Hãy nhập tên hiển thị.")
        @Size(max = 50, message = "Tên hiển thị tối đa 50 ký tự.")
        String displayName,

        @NotBlank(message = "Hãy nhập địa chỉ thư điện tử.")
        @Email(message = "Địa chỉ thư điện tử chưa đúng định dạng.")
        @Size(max = 254, message = "Địa chỉ thư điện tử quá dài.")
        String email,

        @NotNull(message = "Hãy nhập mật khẩu.")
        String password,

        boolean acceptTerms,

        @NotBlank String termsVersion,

        @NotBlank String privacyVersion) {
}
