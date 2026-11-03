package com.learningcompanion.admin.api.dto;

/** Khóa để nhập tay vào ứng dụng sinh mã và địa chỉ otpauth:// để quét mã QR. */
public record TotpSetupResponse(String secret, String otpauthUri) {
}
