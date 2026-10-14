package com.learningcompanion.notification.application;

import com.learningcompanion.notification.infrastructure.NotificationSseHub;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Đẩy số chưa đọc mới tới các thẻ đang mở của người học, chỉ sau khi giao dịch gây ra thay đổi đã ghi xong, để
 * giao diện không bao giờ thấy thông báo của một sự kiện bị hủy.
 */
@Component
public class UnreadCountPublisher {

    private final NotificationSseHub hub;
    private final NotificationService notifications;

    public UnreadCountPublisher(NotificationSseHub hub, NotificationService notifications) {
        this.hub = hub;
        this.notifications = notifications;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void on(NotificationsChanged event) {
        if (hub.hasStreams(event.ownerId())) {
            hub.publish(event.ownerId(), notifications.unreadCountOf(event.ownerId()), event.created());
        }
    }
}
