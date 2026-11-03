package com.learningcompanion.ai.provider;

/**
 * Lời gọi tới nhà cung cấp thất bại. Mã lỗi ngắn được ghi vào sổ đo mức sử dụng; thông điệp không chứa nội dung lời
 * gọi hay khóa bí mật.
 */
public class ProviderCallException extends RuntimeException {

    private final String errorCode;

    public ProviderCallException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ProviderCallException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
