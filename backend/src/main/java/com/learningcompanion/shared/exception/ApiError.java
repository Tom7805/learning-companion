package com.learningcompanion.shared.exception;

import java.time.Instant;
import java.util.List;

/** Thân phản hồi lỗi thống nhất cho mọi điểm cuối. */
public record ApiError(
        String code,
        String message,
        int status,
        List<FieldError> fieldErrors,
        String requestId,
        Instant timestamp) {

    public record FieldError(String field, String code, String message) {
    }
}
