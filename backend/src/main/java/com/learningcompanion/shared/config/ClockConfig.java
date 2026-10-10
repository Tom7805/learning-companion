package com.learningcompanion.shared.config;

import com.learningcompanion.shared.time.AdjustableClock;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Môi trường thử dùng đồng hồ tua được để kiểm tra các mốc hết hạn mà không phải chờ thật. */
    @Bean
    @ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
    public AdjustableClock adjustableClock() {
        return new AdjustableClock(Clock.systemUTC());
    }

    @Bean
    @ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "false", matchIfMissing = true)
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
