package com.learningcompanion.ai.quota.application;

import com.learningcompanion.notification.application.NotificationTargetResolver;
import com.learningcompanion.notification.domain.NotificationTargetType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Thông báo hoàn lượt mở trang lượt trí tuệ nhân tạo và dung lượng của người học (NCL-04-CN-003). */
@Configuration
public class QuotaNotificationTargets {

    public static final String USAGE_PATH = "/usage";

    @Bean
    public NotificationTargetResolver usageNotificationTarget() {
        return NotificationTargetResolver.page(NotificationTargetType.USAGE, USAGE_PATH);
    }
}
