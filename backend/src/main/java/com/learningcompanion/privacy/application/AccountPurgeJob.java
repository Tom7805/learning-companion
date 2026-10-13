package com.learningcompanion.privacy.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Tác vụ hằng ngày xóa vĩnh viễn các tài khoản đã hết ba mươi ngày chờ (NCL-01-CN-009, TC-03). */
@Component
public class AccountPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(AccountPurgeJob.class);

    private final AccountDeletionService deletions;

    public AccountPurgeJob(AccountDeletionService deletions) {
        this.deletions = deletions;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Ho_Chi_Minh")
    public int run() {
        int purged = deletions.purgeDue();
        if (purged > 0) {
            log.info("Permanently deleted {} account(s) after the grace period", purged);
        }
        return purged;
    }
}
