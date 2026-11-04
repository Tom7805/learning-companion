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
    /** Mã chống giả mạo yêu cầu thiếu hoặc không khớp cookie; giao diện lấy mã mới rồi gửi lại một lần. */
    CSRF_TOKEN_INVALID(HttpStatus.FORBIDDEN, "Trang đã cũ, hãy tải lại trang rồi thử lại."),
    AUDIT_LOG_IMMUTABLE(HttpStatus.FORBIDDEN, "Nhật ký thao tác chỉ được ghi thêm, không sửa hay xóa được."),
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
    EXPORT_DAILY_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "Mỗi ngày chỉ yêu cầu xuất dữ liệu được một lần."),
    EXPORT_NOT_READY(HttpStatus.CONFLICT, "Gói dữ liệu đang được chuẩn bị, hãy chờ thêm ít phút."),
    EXPORT_EXPIRED(HttpStatus.GONE, "Gói dữ liệu đã hết hạn tải, hãy yêu cầu xuất lại."),
    REAUTHENTICATION_REQUIRED(HttpStatus.FORBIDDEN, "Hãy xác nhận lại mật khẩu hoặc đăng nhập lại để tiếp tục."),
    ACCOUNT_PENDING_DELETION(HttpStatus.FORBIDDEN, "Tài khoản đang chờ xóa. Hãy hủy yêu cầu xóa để dùng tiếp."),
    /** Bước mã xác thực thứ hai của quản trị viên không còn: hết hạn, đã xong, hoặc chưa qua bước mật khẩu. */
    TWO_FACTOR_CHALLENGE_INVALID(HttpStatus.UNAUTHORIZED, "Bước xác thực đã hết hạn, hãy đăng nhập lại từ đầu."),
    TWO_FACTOR_CODE_INVALID(HttpStatus.BAD_REQUEST, "Mã xác thực chưa đúng hoặc đã hết hạn."),
    RECOVERY_CODE_INVALID(HttpStatus.BAD_REQUEST, "Mã khôi phục chưa đúng hoặc đã được dùng."),
    TWO_FACTOR_ENROLLMENT_NOT_STARTED(HttpStatus.CONFLICT, "Hãy tạo khóa cho ứng dụng sinh mã trước."),
    ADMIN_LOGIN_LOCKED(HttpStatus.LOCKED, "Đăng nhập quản trị đang tạm khóa do nhập sai mã xác thực nhiều lần."),
    /** Hệ thống chỉ có một quản trị viên (NCL-03-CN-002 TC-04). */
    ADMIN_ALREADY_EXISTS(HttpStatus.CONFLICT, "Hệ thống đã có quản trị viên và chỉ cho phép một quản trị viên."),
    /** Lớp kết nối trí tuệ nhân tạo đã thử lại và chuyển dự phòng mà vẫn lỗi; chức năng gọi thử lại sau (NCL-04-CN-001). */
    AI_TEMPORARILY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
            "Dịch vụ trí tuệ nhân tạo đang tạm gián đoạn, hãy thử lại sau ít phút."),
    /** Chỉ bật được nhà cung cấp đã xác nhận cam kết không huấn luyện trên dữ liệu (QTN-11, NCL-04-CN-001 TC-04). */
    AI_PROVIDER_TRAINING_NOT_CONFIRMED(HttpStatus.CONFLICT,
            "Chưa bật được: cần xác nhận nhà cung cấp đã cam kết không dùng dữ liệu học tập để huấn luyện mô hình."),
    AI_PROVIDER_SECRET_MISSING(HttpStatus.CONFLICT,
            "Chưa bật được: kho bí mật chưa có khóa với tên đã khai báo cho nhà cung cấp này."),
    AI_PROVIDER_INCOMPLETE(HttpStatus.CONFLICT, "Chưa bật được: thiếu địa chỉ dịch vụ hoặc tên mô hình."),
    AI_PROVIDER_CODE_TAKEN(HttpStatus.CONFLICT, "Mã nhà cung cấp này đã được dùng."),
    /** Nhà cung cấp đang là nhà cung cấp chính của một khả năng: đổi định tuyến trước rồi mới tắt được. */
    AI_PROVIDER_IN_USE(HttpStatus.CONFLICT,
            "Nhà cung cấp đang được dùng trong định tuyến. Hãy đổi định tuyến trước."),
    AI_ROUTE_INVALID(HttpStatus.BAD_REQUEST,
            "Nhà cung cấp chính và dự phòng phải khác nhau, đang bật và hỗ trợ khả năng này."),
    AI_NO_FALLBACK(HttpStatus.CONFLICT, "Khả năng này chưa có nhà cung cấp dự phòng đang bật."),
    /** Lời nhắc hệ thống có địa chỉ thư hay số điện thoại; chi tiết kèm vị trí từng đoạn (NCL-04-CN-002 TC-03). */
    AI_CONFIG_PROMPT_HAS_PII(HttpStatus.BAD_REQUEST,
            "Lời nhắc hệ thống chứa dữ liệu cá nhân (địa chỉ thư hoặc số điện thoại). Hãy xóa các đoạn được đánh dấu."),
    AI_CONFIG_PROVIDER_INVALID(HttpStatus.BAD_REQUEST,
            "Nhà cung cấp không tồn tại hoặc không hỗ trợ khả năng của chức năng này."),
    AI_CONFIG_PROVIDER_UNAVAILABLE(HttpStatus.CONFLICT,
            "Chưa phát hành được: nhà cung cấp của phiên bản này đang tắt hoặc chưa xác nhận không huấn luyện."),
    AI_CONFIG_TRIAL_REQUIRED(HttpStatus.CONFLICT, "Hãy thử phiên bản này với một câu hỏi mẫu trước khi phát hành."),
    AI_CONFIG_NOT_PUBLISHABLE(HttpStatus.CONFLICT,
            "Phiên bản này đang phát hành hoặc đã bị thu hồi nên không phát hành được."),
    AI_CONFIG_NO_PREVIOUS_VERSION(HttpStatus.CONFLICT, "Chưa có phiên bản phát hành trước đó để quay lại."),
    /**
     * Không còn đủ lượt trí tuệ nhân tạo trong ngày cho tác vụ mới (NCL-04-CN-003, QTN-13). Chi tiết kèm thời điểm hồi
     * lại; mọi thao tác đọc và học không tốn lượt vẫn chạy bình thường.
     */
    AI_QUOTA_EXHAUSTED(HttpStatus.TOO_MANY_REQUESTS,
            "Bạn đã dùng hết lượt trí tuệ nhân tạo hôm nay. Lượt sẽ hồi lại vào nửa đêm; mọi chức năng học khác vẫn dùng bình thường."),
    /** Tệp mới làm vượt dung lượng lưu trữ của người học; chi tiết kèm dung lượng đã dùng và hạn mức. */
    STORAGE_QUOTA_EXCEEDED(HttpStatus.CONFLICT,
            "Kho tài liệu đã gần đầy, không đủ chỗ cho tệp này. Hãy xóa bớt tài liệu cũ rồi thử lại."),
    /**
     * Thư mục cha đã có thư mục hoặc tài liệu cùng tên (không phân biệt hoa thường); chi tiết kèm tên gợi ý còn trống
     * (NCL-05-CN-001 TC-03).
     */
    LIBRARY_NAME_TAKEN(HttpStatus.CONFLICT, "Tên này đã có trong cùng thư mục. Hãy chọn tên khác."),
    /** Không chuyển được thư mục vào chính nó hoặc vào một thư mục con của nó. */
    LIBRARY_FOLDER_CYCLE(HttpStatus.CONFLICT, "Không thể chuyển thư mục vào chính nó hoặc thư mục con của nó."),
    LIBRARY_TAG_NAME_TAKEN(HttpStatus.CONFLICT, "Bạn đã có nhãn với tên này."),
    /**
     * Tệp tải lên không được nhận (QTN-19); {@code details.reason} cho biết vì sao: MALWARE, UNSUPPORTED,
     * PASSWORD_PROTECTED, CORRUPTED, EXPANDS_TOO_LARGE hoặc EMPTY. Tệp không được lưu.
     */
    UPLOAD_REJECTED(HttpStatus.UNPROCESSABLE_CONTENT, "Tệp này không được nhận."),
    /**
     * Kho đã có tài liệu cùng nội dung (QTN-17); chi tiết kèm mã, tên và đường dẫn thư mục của bản đã có để giao diện
     * gợi ý dùng lại bản đó (NCL-05-CN-002 TC-03).
     */
    LIBRARY_DOCUMENT_DUPLICATE(HttpStatus.CONFLICT, "Tài liệu này đã có trong kho của bạn."),
    /** Phần gửi lên không phải phần kế tiếp; chi tiết kèm số byte máy chủ đã nhận để giao diện tải tiếp từ đó. */
    UPLOAD_PART_OUT_OF_ORDER(HttpStatus.CONFLICT, "Phần tệp gửi lên chưa đúng thứ tự."),
    /** Yêu cầu hoàn tất khi máy chủ chưa nhận đủ các phần; chi tiết kèm số byte đã nhận. */
    UPLOAD_INCOMPLETE(HttpStatus.CONFLICT, "Tệp chưa tải lên xong."),
    /** Dịch vụ quét mã độc tạm không trả lời: tệp chưa quét thì không nhận, người học thử lại sau. */
    MALWARE_SCAN_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
            "Chưa kiểm tra được tệp lúc này, hãy thử tải lên lại sau ít phút."),
    /** Chỉ tài liệu xử lý lỗi mới thử xử lý lại được (NCL-05-CN-003). */
    DOCUMENT_NOT_FAILED(HttpStatus.CONFLICT, "Tài liệu này không ở trạng thái lỗi nên không cần thử lại."),
    /** Đường dẫn trang web không đọc được hoặc không dùng giao thức http/https (NCL-05-CN-004). */
    WEB_ADDRESS_INVALID(HttpStatus.UNPROCESSABLE_CONTENT,
            "Đường dẫn chưa hợp lệ. Hãy dán địa chỉ trang bắt đầu bằng http:// hoặc https://."),
    /** Đường dẫn trỏ vào mạng nội bộ hoặc chính máy chủ: không bao giờ lấy nội dung (NCL-05-CN-004 TC-03). */
    WEB_ADDRESS_BLOCKED(HttpStatus.UNPROCESSABLE_CONTENT,
            "Không lấy nội dung từ địa chỉ thuộc mạng nội bộ. Hãy dùng đường dẫn của một trang công khai."),
    /**
     * Không lấy được nội dung trang (NCL-05-CN-004 TC-04); {@code details.reason} là LOGIN_REQUIRED, NOT_FOUND,
     * TIMEOUT, TOO_LARGE, UNSUPPORTED, NO_CONTENT hoặc UNREACHABLE. Giao diện gợi ý dán văn bản thay thế.
     */
    WEB_PAGE_UNAVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT,
            "Không lấy được nội dung trang này. Bạn có thể dán văn bản của trang để thêm vào kho."),
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
