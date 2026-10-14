package com.learningcompanion.auth.application;

import com.learningcompanion.auth.domain.DeviceInfo;
import com.learningcompanion.notification.application.NotificationService;
import com.learningcompanion.notification.domain.NotificationTargetType;
import com.learningcompanion.notification.domain.NotificationType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Cảnh báo bảo mật cũng có trong trung tâm thông báo (NCL-02-CN-002), không chỉ trong thư. Chạy ngay trong giao dịch
 * của sự kiện (đăng nhập, đổi mật khẩu) nên thông báo chỉ tồn tại khi sự kiện đã thật sự xảy ra.
 */
@Component
public class SecurityNotifications {

    private final NotificationService notifications;

    public SecurityNotifications(NotificationService notifications) {
        this.notifications = notifications;
    }

    @EventListener
    public void on(AuthMailNotifier.NewDeviceLoginAlert event) {
        DeviceInfo device = event.device();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("browser", device.browser());
        params.put("operatingSystem", device.operatingSystem());
        params.put("deviceType", device.deviceType().name());
        if (device.location() != null) {
            params.put("location", device.location());
        }
        params.put("localNetwork", device.localNetwork());
        notifications.notify(event.accountId(), NotificationType.NEW_DEVICE_LOGIN, NotificationTargetType.DEVICES,
                null, params, "new-device-login:" + event.sessionId());
    }

    @EventListener
    public void on(AuthMailNotifier.PasswordChangedNotice event) {
        notifications.notify(event.accountId(), NotificationType.PASSWORD_CHANGED, NotificationTargetType.SECURITY,
                null, Map.of("change", event.change().name()),
                "password-changed:" + event.changedAt().toEpochMilli());
    }
}
