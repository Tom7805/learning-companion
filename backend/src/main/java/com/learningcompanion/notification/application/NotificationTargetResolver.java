package com.learningcompanion.notification.application;

import com.learningcompanion.notification.domain.NotificationTargetType;
import java.util.Optional;
import java.util.UUID;

/**
 * Chức năng sở hữu một loại đối tượng cho biết đối tượng đó còn mở được không và mở ở đâu trong giao diện
 * (NCL-02-CN-002). Đối tượng đã bị xóa, hết hạn hay không thuộc người học thì trả về rỗng để giao diện mở trang
 * "nội dung không còn" thay vì báo lỗi.
 */
public interface NotificationTargetResolver {

    NotificationTargetType type();

    /** Đường dẫn trong ứng dụng, ví dụ "/exports/{id}". Chỉ tra trong dữ liệu của {@code ownerId}. */
    Optional<String> pathOf(UUID ownerId, UUID targetId);

    /** Đối tượng là một trang cố định của ứng dụng (ví dụ trang thiết bị), luôn mở được. */
    static NotificationTargetResolver page(NotificationTargetType type, String path) {
        return new NotificationTargetResolver() {
            @Override
            public NotificationTargetType type() {
                return type;
            }

            @Override
            public Optional<String> pathOf(UUID ownerId, UUID targetId) {
                return Optional.of(path);
            }
        };
    }
}
