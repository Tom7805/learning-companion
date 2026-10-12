package com.learningcompanion.privacy.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Phiên bản người học vừa đọc trong hộp thoại; phải đúng bản đang áp dụng. */
public record AcceptTermsRequest(@NotBlank String termsVersion, @NotBlank String privacyVersion) {
}
