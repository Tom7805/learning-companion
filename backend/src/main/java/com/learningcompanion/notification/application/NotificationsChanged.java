package com.learningcompanion.notification.application;

import java.util.UUID;

/**
 * Số thông báo chưa đọc của một người học vừa đổi (có thông báo mới, đã đọc một hay tất cả). Được phát trong giao
 * dịch gây ra thay đổi; {@link UnreadCountPublisher} chỉ đẩy số mới tới giao diện sau khi giao dịch ghi xong.
 *
 * @param created có thông báo mới, giao diện tải lại danh sách nếu đang mở
 */
public record NotificationsChanged(UUID ownerId, boolean created) {
}
