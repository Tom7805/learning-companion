package com.learningcompanion.library.application;

import com.learningcompanion.shared.storage.ChunkedUploadStore;
import com.learningcompanion.shared.storage.UploadProperties;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dọn các lần tải lên bị bỏ dở quá hạn cùng các phần đã nhận, và các phiên đã xong đủ lâu (chỉ còn để trả lời lại yêu
 * cầu hoàn tất gửi trùng).
 */
@Component
public class UploadCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(UploadCleanupJob.class);

    private final JdbcTemplate jdbc;
    private final ChunkedUploadStore parts;
    private final UploadProperties properties;
    private final Clock clock;

    public UploadCleanupJob(JdbcTemplate jdbc, ChunkedUploadStore parts, UploadProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.parts = parts;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT10M")
    @Transactional
    public int purgeExpired() {
        Instant now = clock.instant();
        List<Map<String, Object>> expired = jdbc.queryForList(
                "select id, owner_id from library_upload_sessions where status = 'UPLOADING' and expires_at < ?",
                Timestamp.from(now));
        for (Map<String, Object> row : expired) {
            parts.discard((UUID) row.get("owner_id"), (UUID) row.get("id"));
            jdbc.update("delete from library_upload_sessions where id = ?", row.get("id"));
        }
        int finished = jdbc.update("delete from library_upload_sessions where status = 'COMPLETED' and updated_at < ?",
                Timestamp.from(now.minus(properties.sessionTtl())));
        if (!expired.isEmpty() || finished > 0) {
            log.info("Đã dọn {} lần tải bỏ dở và {} lần tải đã xong", expired.size(), finished);
        }
        return expired.size() + finished;
    }
}
