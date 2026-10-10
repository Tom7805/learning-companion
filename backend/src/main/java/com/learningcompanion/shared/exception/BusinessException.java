package com.learningcompanion.shared.exception;

import java.util.Map;

/** Lỗi nghiệp vụ có mã ổn định, có thể gắn với một trường cụ thể của biểu mẫu và kèm dữ liệu cho giao diện. */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final String field;
    private final Map<String, Object> details;

    public BusinessException(ErrorCode code) {
        this(code, null);
    }

    public BusinessException(ErrorCode code, String field) {
        this(code, field, Map.of());
    }

    public BusinessException(ErrorCode code, String field, Map<String, Object> details) {
        super(code.defaultMessage());
        this.code = code;
        this.field = field;
        this.details = details;
    }

    public ErrorCode code() {
        return code;
    }

    public String field() {
        return field;
    }

    public Map<String, Object> details() {
        return details;
    }
}
