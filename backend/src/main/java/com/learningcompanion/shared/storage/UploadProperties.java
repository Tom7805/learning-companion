package com.learningcompanion.shared.storage;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tải tệp lên theo từng phần (NCL-05-CN-002). {@code chunkSizeBytes}: cỡ mỗi phần giao diện gửi; {@code sessionTtl}:
 * phiên bỏ dở quá lâu thì bị dọn cùng các phần đã nhận. {@code malwareScanner}: "signature" (máy phát triển và kiểm
 * thử, nhận mẫu thử EICAR) hoặc "clamav" (môi trường thật, gọi clamd qua {@code clamav.host}:{@code clamav.port}).
 * Giới hạn bung tệp nén chặn tệp nén nhỏ bung ra hàng gigabyte.
 */
@ConfigurationProperties(prefix = "app.uploads")
public record UploadProperties(Integer chunkSizeBytes, Duration sessionTtl, String malwareScanner, ClamAv clamav,
                               Long maxInflatedBytes, Integer maxInflateRatio) {

    public static final int DEFAULT_CHUNK_SIZE = 2 * 1024 * 1024;

    public UploadProperties {
        if (chunkSizeBytes == null || chunkSizeBytes <= 0) {
            chunkSizeBytes = DEFAULT_CHUNK_SIZE;
        }
        if (sessionTtl == null) {
            sessionTtl = Duration.ofHours(24);
        }
        if (malwareScanner == null || malwareScanner.isBlank()) {
            malwareScanner = "signature";
        }
        if (clamav == null) {
            clamav = new ClamAv("127.0.0.1", 3310, Duration.ofSeconds(30));
        }
        if (maxInflatedBytes == null || maxInflatedBytes <= 0) {
            maxInflatedBytes = 512L * 1024 * 1024;
        }
        if (maxInflateRatio == null || maxInflateRatio <= 0) {
            maxInflateRatio = 100;
        }
    }

    public static UploadProperties defaults() {
        return new UploadProperties(null, null, null, null, null, null);
    }

    public record ClamAv(String host, int port, Duration timeout) {
    }
}
