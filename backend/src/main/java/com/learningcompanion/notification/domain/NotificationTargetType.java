package com.learningcompanion.notification.domain;

/**
 * Loại đối tượng một thông báo trỏ tới. Mỗi loại có một {@code NotificationTargetResolver} ở chức năng sở hữu đối
 * tượng đó, trả về đường dẫn để mở hoặc báo đối tượng không còn.
 */
public enum NotificationTargetType {
    /** Một gói dữ liệu đã xuất; có mã đối tượng. */
    DATA_EXPORT,
    /** Trang thiết bị đang đăng nhập; không có mã đối tượng. */
    DEVICES,
    /** Trang bảo mật tài khoản; không có mã đối tượng. */
    SECURITY
}
