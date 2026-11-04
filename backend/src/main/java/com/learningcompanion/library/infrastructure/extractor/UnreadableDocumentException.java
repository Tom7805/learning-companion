package com.learningcompanion.library.infrastructure.extractor;

/** Tệp không đọc được nội dung: hỏng, cấu trúc lạ hoặc thiếu phần bắt buộc. */
public class UnreadableDocumentException extends RuntimeException {

    public UnreadableDocumentException(String message) {
        super(message);
    }

    public UnreadableDocumentException(String message, Throwable cause) {
        super(message, cause);
    }
}
