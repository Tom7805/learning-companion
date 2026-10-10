package com.learningcompanion.auth.api.dto;

/** Cùng một phản hồi cho mọi trường hợp, dù địa chỉ thư đã có tài khoản hay chưa. */
public record RegistrationAcceptedResponse(String email, long resendAvailableInSeconds) {
}
