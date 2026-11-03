package com.learningcompanion.ai.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CircuitBreakerRegistryTest {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Duration OPEN_FOR = Duration.ofSeconds(60);

    private final MutableClock clock = new MutableClock();
    private final CircuitBreakerRegistry breakers = new CircuitBreakerRegistry(clock);

    @Test
    void opensAfterFiveConsecutiveFailuresWithinOneMinute() {
        for (int i = 0; i < 4; i++) {
            assertThat(breakers.recordFailure(AiCapability.TEXT_GENERATION, "a", 5, WINDOW)).isFalse();
        }
        assertThat(breakers.allows(AiCapability.TEXT_GENERATION, "a", OPEN_FOR)).isTrue();

        assertThat(breakers.recordFailure(AiCapability.TEXT_GENERATION, "a", 5, WINDOW)).isTrue();

        assertThat(breakers.allows(AiCapability.TEXT_GENERATION, "a", OPEN_FOR)).isFalse();
        // Mạch tách riêng theo khả năng và nhà cung cấp.
        assertThat(breakers.allows(AiCapability.EMBEDDING, "a", OPEN_FOR)).isTrue();
        assertThat(breakers.allows(AiCapability.TEXT_GENERATION, "b", OPEN_FOR)).isTrue();
    }

    @Test
    void failuresOutsideTheWindowDoNotCount() {
        for (int i = 0; i < 4; i++) {
            breakers.recordFailure(AiCapability.OCR, "a", 5, WINDOW);
        }
        clock.advance(Duration.ofSeconds(61));
        assertThat(breakers.recordFailure(AiCapability.OCR, "a", 5, WINDOW)).isFalse();
        assertThat(breakers.snapshot(AiCapability.OCR, "a", OPEN_FOR).recentFailures()).isEqualTo(1);
    }

    @Test
    void successResetsTheCount() {
        for (int i = 0; i < 4; i++) {
            breakers.recordFailure(AiCapability.OCR, "a", 5, WINDOW);
        }
        breakers.recordSuccess(AiCapability.OCR, "a");
        assertThat(breakers.recordFailure(AiCapability.OCR, "a", 5, WINDOW)).isFalse();
    }

    @Test
    void halfOpensAfterTheOpenPeriodAndClosesOnSuccess() {
        for (int i = 0; i < 5; i++) {
            breakers.recordFailure(AiCapability.VISION, "a", 5, WINDOW);
        }
        clock.advance(OPEN_FOR);
        assertThat(breakers.allows(AiCapability.VISION, "a", OPEN_FOR)).isTrue();
        assertThat(breakers.snapshot(AiCapability.VISION, "a", OPEN_FOR).state())
                .isEqualTo(CircuitBreakerRegistry.State.HALF_OPEN);

        // Lời gọi thử lỗi: mở lại, không cảnh báo lần nữa.
        assertThat(breakers.recordFailure(AiCapability.VISION, "a", 5, WINDOW)).isFalse();
        assertThat(breakers.allows(AiCapability.VISION, "a", OPEN_FOR)).isFalse();

        clock.advance(OPEN_FOR);
        assertThat(breakers.allows(AiCapability.VISION, "a", OPEN_FOR)).isTrue();
        breakers.recordSuccess(AiCapability.VISION, "a");
        assertThat(breakers.snapshot(AiCapability.VISION, "a", OPEN_FOR).state())
                .isEqualTo(CircuitBreakerRegistry.State.CLOSED);
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-11-03T08:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
