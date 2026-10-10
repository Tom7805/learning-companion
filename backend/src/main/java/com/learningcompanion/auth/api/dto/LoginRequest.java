package com.learningcompanion.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Hãy nhập địa chỉ thư điện tử.")
        @Email(message = "Địa chỉ thư điện tử chưa đúng định dạng.")
        @Size(max = 254)
        String email,

        @NotBlank(message = "Hãy nhập mật khẩu.")
        @Size(max = 256)
        String password,

        boolean rememberDevice) {
}
