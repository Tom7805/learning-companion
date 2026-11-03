package com.learningcompanion.ai.gateway;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Ngắt mạch theo từng cặp khả năng và nhà cung cấp (NCL-04-CN-001 TC-02). Đủ số lỗi liên tiếp trong một khoảng thời
 * gian thì mạch mở: lời gọi mới đi thẳng sang dự phòng. Hết thời gian mở, mạch cho một lời gọi thử; thành công thì
 * đóng lại, lỗi thì mở tiếp. Trạng thái nằm trong bộ nhớ của từng máy chủ.
 */
@Component
public class CircuitBreakerRegistry {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    public record Snapshot(State state, int recentFailures, Instant openedAt, Instant retryAt) {
    }

    private final Map<String, Breaker> breakers = new HashMap<>();
    private final Clock clock;

    public CircuitBreakerRegistry(Clock clock) {
        this.clock = clock;
    }

    /** Có được gọi nhà cung cấp này không; mạch mở đã hết hạn thì chuyển sang thử lại một lần. */
    public synchronized boolean allows(AiCapability capability, String providerCode, Duration openFor) {
        Breaker breaker = breakers.get(key(capability, providerCode));
        if (breaker == null || breaker.state == State.CLOSED || breaker.state == State.HALF_OPEN) {
            return true;
        }
        if (!clock.instant().isBefore(breaker.openedAt.plus(openFor))) {
            breaker.state = State.HALF_OPEN;
            return true;
        }
        return false;
    }

    public synchronized void recordSuccess(AiCapability capability, String providerCode) {
        breakers.remove(key(capability, providerCode));
    }

    /**
     * Ghi một lần lỗi.
     *
     * @return true nếu lần lỗi này làm mạch vừa mở (cần cảnh báo quản trị viên)
     */
    public synchronized boolean recordFailure(AiCapability capability, String providerCode, int threshold,
                                              Duration window) {
        Instant now = clock.instant();
        Breaker breaker = breakers.computeIfAbsent(key(capability, providerCode), ignored -> new Breaker());
        if (breaker.state == State.HALF_OPEN) {
            breaker.state = State.OPEN;
            breaker.openedAt = now;
            return false;
        }
        breaker.failures.addLast(now);
        while (!breaker.failures.isEmpty() && breaker.failures.peekFirst().isBefore(now.minus(window))) {
            breaker.failures.removeFirst();
        }
        if (breaker.state == State.CLOSED && breaker.failures.size() >= threshold) {
            breaker.state = State.OPEN;
            breaker.openedAt = now;
            return true;
        }
        return false;
    }

    public synchronized Snapshot snapshot(AiCapability capability, String providerCode, Duration openFor) {
        Breaker breaker = breakers.get(key(capability, providerCode));
        if (breaker == null) {
            return new Snapshot(State.CLOSED, 0, null, null);
        }
        Instant retryAt = breaker.openedAt == null ? null : breaker.openedAt.plus(openFor);
        return new Snapshot(breaker.state, breaker.failures.size(), breaker.openedAt, retryAt);
    }

    /** Đóng mọi mạch, ví dụ sau khi quản trị viên đổi định tuyến hoặc trong kiểm thử. */
    public synchronized void reset() {
        breakers.clear();
    }

    public synchronized void reset(AiCapability capability) {
        breakers.keySet().removeIf(key -> key.startsWith(capability.name() + "|"));
    }

    private static String key(AiCapability capability, String providerCode) {
        return capability.name() + "|" + providerCode;
    }

    private static final class Breaker {
        private final Deque<Instant> failures = new ArrayDeque<>();
        private State state = State.CLOSED;
        private Instant openedAt;
    }
}
