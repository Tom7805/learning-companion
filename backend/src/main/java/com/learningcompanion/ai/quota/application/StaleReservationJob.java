package com.learningcompanion.ai.quota.application;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Năm phút một lần hoàn lại các lần giữ chỗ treo quá {@link QuotaReservationService#STALE_AFTER}, ví dụ khi máy chủ
 * khởi động lại giữa lúc tác vụ đang chạy: người học không bao giờ mất lượt cho tác vụ không có kết quả.
 */
@Component
public class StaleReservationJob {

    private static final Logger log = LoggerFactory.getLogger(StaleReservationJob.class);

    private final QuotaReservationService reservations;
    private final Clock clock;

    public StaleReservationJob(QuotaReservationService reservations, Clock clock) {
        this.reservations = reservations;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public int run() {
        int released = reservations.releaseStale(clock.instant().minus(QuotaReservationService.STALE_AFTER), 500);
        if (released > 0) {
            log.info("Đã hoàn lại {} lần giữ chỗ lượt bị treo", released);
        }
        return released;
    }
}
