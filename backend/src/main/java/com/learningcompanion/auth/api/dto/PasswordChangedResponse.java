package com.learningcompanion.auth.api.dto;

/** Số thiết bị khác vừa bị đăng xuất sau khi đổi mật khẩu. */
public record PasswordChangedResponse(int revokedSessions) {
}
