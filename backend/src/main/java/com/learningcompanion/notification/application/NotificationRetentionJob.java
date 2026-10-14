package com.learningcompanion.notification.application;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Mỗi đêm xóa thông báo đã quá chín mươi ngày (NCL-02-CN-002). */
@Component
public class NotificationRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetentionJob.class);

    private final NotificationService notifications;
    private final Clock clock;

    public NotificationRetentionJob(NotificationService notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Ho_Chi_Minh")
    public int run() {
        int removed = notifications.purgeOlderThan(clock.instant().minus(NotificationService.RETENTION));
        if (removed > 0) {
            log.info("Đã xóa {} thông báo quá {} ngày", removed, NotificationService.RETENTION.toDays());
        }
        return removed;
    }
}
