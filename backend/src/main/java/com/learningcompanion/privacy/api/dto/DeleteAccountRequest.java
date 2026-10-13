package com.learningcompanion.privacy.api.dto;

import jakarta.validation.constraints.Size;

/** Mật khẩu xác nhận xóa tài khoản; tài khoản chưa có mật khẩu thì để trống và phải vừa đăng nhập lại. */
public record DeleteAccountRequest(@Size(max = 128) String password) {
}
