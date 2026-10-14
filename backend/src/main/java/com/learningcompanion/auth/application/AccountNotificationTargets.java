package com.learningcompanion.auth.application;

import com.learningcompanion.notification.application.NotificationTargetResolver;
import com.learningcompanion.notification.domain.NotificationTargetType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Cảnh báo bảo mật trong trung tâm thông báo mở trang thiết bị hoặc trang bảo mật của tài khoản. */
@Configuration
public class AccountNotificationTargets {

    @Bean
    public NotificationTargetResolver devicesNotificationTarget() {
        return NotificationTargetResolver.page(NotificationTargetType.DEVICES, "/devices");
    }

    @Bean
    public NotificationTargetResolver securityNotificationTarget() {
        return NotificationTargetResolver.page(NotificationTargetType.SECURITY, "/security");
    }
}
