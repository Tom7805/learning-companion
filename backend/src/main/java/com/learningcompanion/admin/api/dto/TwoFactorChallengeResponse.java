package com.learningcompanion.admin.api.dto;

import java.time.Instant;

/** Bước mã xác thực đang chờ: nhập mã (VERIFY) hay thiết lập ứng dụng sinh mã (ENROLL), và hạn của bước này. */
public record TwoFactorChallengeResponse(String stage, String email, Instant expiresAt) {
}
