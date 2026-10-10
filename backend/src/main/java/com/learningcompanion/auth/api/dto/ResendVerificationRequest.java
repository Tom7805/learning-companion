package com.learningcompanion.auth.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Gửi lại bằng địa chỉ thư, hoặc bằng đường dẫn đã hết hạn để không phải nhập lại địa chỉ. */
public record ResendVerificationRequest(
        @Email(message = "Địa chỉ thư điện tử chưa đúng định dạng.") @Size(max = 254) String email,
        @Size(max = 128) String token) {

    @AssertTrue(message = "Cần địa chỉ thư hoặc đường dẫn xác thực.")
    public boolean isEmailOrTokenPresent() {
        return (email != null && !email.isBlank()) != (token != null && !token.isBlank());
    }
}
