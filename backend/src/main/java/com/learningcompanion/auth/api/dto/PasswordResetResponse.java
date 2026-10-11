package com.learningcompanion.auth.api.dto;

/** Địa chỉ thư của tài khoản vừa đặt lại mật khẩu, để điền sẵn ở màn hình đăng nhập. */
public record PasswordResetResponse(String email) {
}
