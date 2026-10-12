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
    RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST,
            "Đường dẫn đặt lại mật khẩu không hợp lệ, đã được dùng hoặc đã được thay bằng thư mới hơn."),
    RESET_TOKEN_EXPIRED(HttpStatus.GONE, "Đường dẫn đặt lại mật khẩu đã hết hạn."),
    CURRENT_PASSWORD_INVALID(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại chưa đúng."),
    PASSWORD_REUSED(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải khác mật khẩu đang dùng."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập để tiếp tục."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu."),
    FILE_LINK_EXPIRED(HttpStatus.GONE, "Đường dẫn mở tệp đã hết hạn."),
    OAUTH_STATE_INVALID(HttpStatus.BAD_REQUEST, "Không xác minh được yêu cầu đăng nhập, hãy thử lại từ đầu."),
    OAUTH_CANCELLED(HttpStatus.BAD_REQUEST, "Bạn đã hủy đăng nhập qua nhà cung cấp định danh."),
    OAUTH_EMAIL_UNVERIFIED(HttpStatus.BAD_REQUEST, "Địa chỉ thư của tài khoản bên ngoài chưa được xác thực."),
    OAUTH_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "Nhà cung cấp định danh đang gặp sự cố, hãy thử lại sau."),
    OAUTH_PENDING_INVALID(HttpStatus.GONE, "Phiên đăng nhập đã hết hạn, hãy bắt đầu lại."),
    OAUTH_DISABLED(HttpStatus.NOT_FOUND, "Chưa hỗ trợ đăng nhập bằng nhà cung cấp này."),
    EXTERNAL_IDENTITY_IN_USE(HttpStatus.CONFLICT, "Tài khoản bên ngoài này đã liên kết với một tài khoản khác."),
    EXTERNAL_IDENTITY_ALREADY_LINKED(HttpStatus.CONFLICT, "Tài khoản của bạn đã liên kết với nhà cung cấp này."),
    PASSWORD_REQUIRED_TO_UNLINK(HttpStatus.CONFLICT, "Hãy đặt mật khẩu trước khi gỡ liên kết, nếu không bạn sẽ không còn cách đăng nhập."),
    PASSWORD_ALREADY_SET(HttpStatus.CONFLICT, "Tài khoản đã có mật khẩu, hãy dùng chức năng đổi mật khẩu."),
    TERMS_ACCEPTANCE_REQUIRED(HttpStatus.FORBIDDEN, "Điều khoản vừa được cập nhật, hãy đọc và đồng ý để dùng tiếp."),
    CONSENT_PURPOSE_REQUIRED(HttpStatus.BAD_REQUEST, "Mục đích này bắt buộc để dùng dịch vụ, không tắt được."),
    DISPLAY_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "Hãy nhập tên hiển thị."),
    DISPLAY_NAME_TOO_LONG(HttpStatus.BAD_REQUEST, "Tên hiển thị tối đa 50 ký tự."),
    INVALID_TIME_ZONE(HttpStatus.BAD_REQUEST, "Múi giờ không hợp lệ."),
    INVALID_PREFERENCE(HttpStatus.BAD_REQUEST, "Giá trị tùy chọn không hợp lệ."),
    AVATAR_INVALID_FORMAT(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Ảnh đại diện cần là tệp JPG, PNG hoặc WebP."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Tệp quá lớn."),
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
