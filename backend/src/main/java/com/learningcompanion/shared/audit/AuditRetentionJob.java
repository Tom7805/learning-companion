package com.learningcompanion.shared.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nhật ký thao tác được lưu một năm (NCL-03-CN-001). Mỗi đêm xóa các dòng đã quá hạn qua đường riêng mà trigger
 * chỉ-ghi-thêm cho phép: cờ {@code app.audit_retention} chỉ bật trong giao dịch này, và trigger tự kiểm lại tuổi
 * từng dòng theo đồng hồ của cơ sở dữ liệu, nên không dòng nào chưa đủ một năm bị xóa được.
 */
@Component
public class AuditRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);

    private final JdbcTemplate jdbc;

    public AuditRetentionJob(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "0 15 4 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public int run() {
        jdbc.queryForObject("select set_config('app.audit_retention', 'on', true)", String.class);
        int removed = jdbc.update("delete from audit_log where occurred_at < now() - interval '365 days'");
        jdbc.queryForObject("select set_config('app.audit_retention', 'off', true)", String.class);
        if (removed > 0) {
            log.info("Removed {} audit log row(s) older than one year", removed);
        }
        return removed;
    }
}
