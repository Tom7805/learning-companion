package com.learningcompanion.shared.security;

import com.learningcompanion.shared.exception.RateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Giới hạn tần suất theo cửa sổ cố định, giữ trong bộ nhớ của một máy chủ.
 * Khi chạy nhiều máy chủ cần chuyển sang kho dùng chung.
 */
@Component
public class RateLimiter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Ném lỗi khi vượt giới hạn. */
    public void check(String key, int limit, Duration window) {
        if (!tryAcquire(key, limit, window)) {
            Window current = windows.get(key);
            Duration retryAfter = current == null
                    ? window
                    : Duration.between(Instant.now(clock), current.start().plus(window));
            throw new RateLimitExceededException(retryAfter.isNegative() ? Duration.ZERO : retryAfter);
        }
    }

    /** Trả về false khi vượt giới hạn, dùng cho những chỗ phải im lặng bỏ qua. */
    public boolean tryAcquire(String key, int limit, Duration window) {
        Instant now = Instant.now(clock);
        Window updated = windows.compute(key, (k, current) -> {
            if (current == null || !now.isBefore(current.start().plus(window))) {
                return new Window(now, 1);
            }
            return new Window(current.start(), current.count() + 1);
        });
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().start().plus(window)));
        }
        return updated.count() <= limit;
    }

    private record Window(Instant start, int count) {
    }
}
