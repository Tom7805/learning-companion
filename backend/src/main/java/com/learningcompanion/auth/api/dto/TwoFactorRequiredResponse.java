package com.learningcompanion.auth.api.dto;

/** Mật khẩu quản trị viên đã đúng, còn bước mã xác thực: {@code stage} là VERIFY (nhập mã) hoặc ENROLL (thiết lập). */
public record TwoFactorRequiredResponse(boolean twoFactorRequired, String stage) {
}
