package com.learningcompanion.library.infrastructure.web;

/** Không lấy được nội dung trang; {@link Reason} được trả cho giao diện để giải thích và gợi ý dán văn bản. */
public class WebFetchException extends RuntimeException {

    public enum Reason {
        /** Trang yêu cầu đăng nhập hoặc chặn truy cập (401, 403, hay chỉ có ô đăng nhập). */
        LOGIN_REQUIRED,
        NOT_FOUND,
        TIMEOUT,
        TOO_LARGE,
        /** Không phải trang HTML hay văn bản. */
        UNSUPPORTED,
        /** Trang không có nội dung chữ nào đọc được. */
        NO_CONTENT,
        /** Không kết nối được, lỗi máy chủ của trang hay chuyển hướng quá nhiều lần. */
        UNREACHABLE
    }

    private final transient Reason reason;

    public WebFetchException(Reason reason) {
        this(reason, null);
    }

    public WebFetchException(Reason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
