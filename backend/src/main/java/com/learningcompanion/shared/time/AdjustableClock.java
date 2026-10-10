package com.learningcompanion.shared.time;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/** Đồng hồ cộng thêm một khoảng lệch, chỉ dùng ở môi trường phát triển và kiểm thử. */
public class AdjustableClock extends Clock {

    private final Clock base;
    private final AtomicReference<Duration> offset = new AtomicReference<>(Duration.ZERO);

    public AdjustableClock(Clock base) {
        this.base = base;
    }

    public void advance(Duration amount) {
        offset.accumulateAndGet(amount, Duration::plus);
    }

    public void reset() {
        offset.set(Duration.ZERO);
    }

    public Duration offset() {
        return offset.get();
    }

    @Override
    public ZoneId getZone() {
        return base.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        AdjustableClock clock = new AdjustableClock(base.withZone(zone));
        clock.offset.set(offset.get());
        return clock;
    }

    @Override
    public Instant instant() {
        return base.instant().plus(offset.get());
    }
}
