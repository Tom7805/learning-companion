package com.learningcompanion.shared.audit;

public enum AuditObjectType {
    USER_ACCOUNT,
    VERIFICATION_TOKEN,
    CONSENT,
    SESSION,
    DOCUMENT,
    PROFILE,
    EXTERNAL_IDENTITY,
    DATA_EXPORT,
    NOTIFICATION,
    UI_PREFERENCES,
    /** Một điểm cuối của hệ thống, ví dụ khu quản trị; mã đối tượng để trống, đường dẫn nằm trong chi tiết. */
    ENDPOINT,
    AUDIT_LOG,
    AI_PROVIDER,
    /** Định tuyến một khả năng trí tuệ nhân tạo; mã đối tượng để trống, khả năng nằm trong chi tiết. */
    AI_ROUTE,
    /** Một phiên bản cấu hình của chức năng trí tuệ nhân tạo; chức năng và số phiên bản nằm trong chi tiết. */
    AI_CONFIG,
    /** Hạn mức lượt trí tuệ nhân tạo và dung lượng lưu trữ, kèm bảng quy đổi lượt; mã đối tượng để trống. */
    QUOTA_POLICY,
    /** Ngưỡng lọc nội dung không an toàn; mã đối tượng để trống, các nhóm đổi nằm trong chi tiết. */
    CONTENT_SAFETY,
    /** Một lần bộ lọc an toàn can thiệp khi lời gọi không gắn với đối tượng nào; mã đối tượng là mã sự kiện. */
    AI_SAFETY_EVENT,
    /** Một thư mục trong kho tài liệu của người học. */
    LIBRARY_FOLDER,
    /** Một nhãn trong kho tài liệu của người học. */
    LIBRARY_TAG,
    /** Một lần tải tệp lên kho tài liệu (NCL-05-CN-002). */
    LIBRARY_UPLOAD
}
