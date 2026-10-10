package com.learningcompanion.shared.exception;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Thân phản hồi lỗi thống nhất cho mọi điểm cuối; `details` mang dữ liệu riêng của từng lỗi. */
public record ApiError(
        String code,
        String message,
        int status,
        List<FieldError> fieldErrors,
        Map<String, Object> details,
        String requestId,
        Instant timestamp) {

    public record FieldError(String field, String code, String message) {
    }
}
