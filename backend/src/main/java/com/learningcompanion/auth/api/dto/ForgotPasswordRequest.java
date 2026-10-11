package com.learningcompanion.auth.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Yêu cầu thư đặt lại bằng địa chỉ thư, hoặc bằng đường dẫn đã hết hạn để không phải nhập lại địa chỉ. */
public record ForgotPasswordRequest(
        @Email(message = "Địa chỉ thư điện tử chưa đúng định dạng.") @Size(max = 254) String email,
        @Size(max = 128) String token) {

    @AssertTrue(message = "Cần địa chỉ thư hoặc đường dẫn đặt lại mật khẩu.")
    public boolean isEmailOrTokenPresent() {
        return (email != null && !email.isBlank()) != (token != null && !token.isBlank());
    }
}
