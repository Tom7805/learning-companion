package com.learningcompanion.notification.domain;

/**
 * Loại thông báo. Giao diện dựng tiêu đề, nội dung và biểu tượng từ loại cùng tham số đi kèm, nên chức năng mới chỉ
 * cần thêm một loại ở đây và bản dịch tương ứng ở giao diện.
 */
public enum NotificationType {
    /** Gói dữ liệu cá nhân đã dựng xong, tải được trong bảy ngày (NCL-01-CN-008). */
    DATA_EXPORT_READY,
    /** Tài khoản vừa đăng nhập trên một thiết bị chưa từng dùng (NCL-01-CN-002). */
    NEW_DEVICE_LOGIN,
    /** Mật khẩu vừa được đổi, đặt lại hoặc đặt lần đầu (NCL-01-CN-003). */
    PASSWORD_CHANGED,
    /** Tác vụ trí tuệ nhân tạo lỗi do dịch vụ bên ngoài; lượt đã giữ chỗ được hoàn lại đủ (NCL-04-CN-003). */
    AI_TASK_REFUNDED,
    /** Tài liệu tải lên đã xử lý xong, sẵn sàng cho hỏi đáp và tạo học liệu (NCL-05-CN-003). */
    DOCUMENT_READY,
    /** Tài liệu tải lên xử lý lỗi; tham số kèm lý do và trang lỗi (NCL-05-CN-003). */
    DOCUMENT_PROCESSING_FAILED
}
