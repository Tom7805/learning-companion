package com.learningcompanion.shared.exception;

/** Lỗi nghiệp vụ có mã ổn định, có thể gắn với một trường cụ thể của biểu mẫu. */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final String field;

    public BusinessException(ErrorCode code) {
        this(code, null);
    }

    public BusinessException(ErrorCode code, String field) {
        super(code.defaultMessage());
        this.code = code;
        this.field = field;
    }

    public ErrorCode code() {
        return code;
    }

    public String field() {
        return field;
    }
}
