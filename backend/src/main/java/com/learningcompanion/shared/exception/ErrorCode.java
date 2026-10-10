package com.learningcompanion.shared.exception;

import org.springframework.http.HttpStatus;

/** Mã lỗi ổn định trả cho giao diện, giao diện dịch mã này sang lời nhắn hiển thị. */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên chưa hợp lệ."),
    PASSWORD_TOO_SHORT(HttpStatus.BAD_REQUEST, "Mật khẩu cần dài ít nhất 10 ký tự."),
    PASSWORD_TOO_LONG(HttpStatus.BAD_REQUEST, "Mật khẩu dài tối đa 128 ký tự."),
    PASSWORD_BREACHED(HttpStatus.BAD_REQUEST,
            "Mật khẩu này đã từng bị lộ trong các vụ rò rỉ dữ liệu. Hãy dùng một cụm từ dài hơn, khó đoán hơn."),
    TERMS_NOT_ACCEPTED(HttpStatus.BAD_REQUEST, "Bạn cần đồng ý điều khoản và chính sách quyền riêng tư."),
    TERMS_VERSION_OUTDATED(HttpStatus.CONFLICT, "Điều khoản vừa được cập nhật, hãy tải lại trang và đọc bản mới."),
    VERIFICATION_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "Đường dẫn xác thực không hợp lệ hoặc đã được thay bằng thư mới hơn."),
    VERIFICATION_TOKEN_USED(HttpStatus.CONFLICT, "Đường dẫn xác thực này đã được dùng."),
    VERIFICATION_TOKEN_EXPIRED(HttpStatus.GONE, "Đường dẫn xác thực đã hết hạn."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Bạn thao tác quá nhanh, hãy thử lại sau ít phút."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Thư điện tử hoặc mật khẩu chưa đúng."),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Đăng nhập đang tạm khóa do nhập sai mật khẩu nhiều lần."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Địa chỉ thư chưa được xác thực."),
    ACCOUNT_UNAVAILABLE(HttpStatus.FORBIDDEN, "Tài khoản này hiện không đăng nhập được."),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn."),
    SESSION_REVOKED(HttpStatus.UNAUTHORIZED, "Thiết bị này đã bị đăng xuất."),
    REVOKE_LINK_INVALID(HttpStatus.BAD_REQUEST, "Đường dẫn đăng xuất thiết bị không hợp lệ."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập để tiếp tục."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Hệ thống gặp sự cố, hãy thử lại sau.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
