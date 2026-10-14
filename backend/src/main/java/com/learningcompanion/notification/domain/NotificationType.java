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
    PASSWORD_CHANGED
}
