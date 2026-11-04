package com.learningcompanion.library.application.ingestion;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tác vụ nền xử lý tài liệu (NCL-05-CN-003). {@code enabled}: tắt thì tài liệu nằm ở trạng thái chờ xử lý (kiểm thử của
 * chức năng tải lên); {@code workers}: số tài liệu xử lý cùng lúc trên một máy chủ; {@code staleAfter}: tài liệu đang
 * xử lý quá lâu (máy chủ dừng giữa chừng) được nhận lại; {@code sweepInterval}: chu kỳ quét hàng chờ.
 */
@ConfigurationProperties(prefix = "app.library.processing")
public record ProcessingProperties(Boolean enabled, Integer workers, Duration staleAfter, Duration sweepInterval) {

    public ProcessingProperties {
        if (enabled == null) {
            enabled = true;
        }
        if (workers == null || workers <= 0) {
            workers = 2;
        }
        if (staleAfter == null) {
            staleAfter = Duration.ofMinutes(10);
        }
        if (sweepInterval == null) {
            sweepInterval = Duration.ofSeconds(30);
        }
    }
}
