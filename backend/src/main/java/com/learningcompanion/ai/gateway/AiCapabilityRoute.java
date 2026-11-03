package com.learningcompanion.ai.gateway;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Định tuyến của một khả năng: nhà cung cấp chính, dự phòng, thời gian chờ mỗi lần gọi, số lần thử lại và ngưỡng ngắt
 * mạch (số lỗi liên tiếp trong một khoảng, rồi mạch mở trong bao lâu).
 */
@Entity
@Table(name = "ai_capability_routes")
public class AiCapabilityRoute {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private AiCapability capability;

    @Column(name = "primary_provider_id", nullable = false)
    private UUID primaryProviderId;

    @Column(name = "fallback_provider_id")
    private UUID fallbackProviderId;

    @Column(name = "timeout_seconds", nullable = false)
    private int timeoutSeconds;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    @Column(name = "breaker_failure_threshold", nullable = false)
    private int breakerFailureThreshold;

    @Column(name = "breaker_window_seconds", nullable = false)
    private int breakerWindowSeconds;

    @Column(name = "breaker_open_seconds", nullable = false)
    private int breakerOpenSeconds;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected AiCapabilityRoute() {
    }

    public void route(UUID primaryProviderId, UUID fallbackProviderId, Instant now) {
        this.primaryProviderId = primaryProviderId;
        this.fallbackProviderId = fallbackProviderId;
        this.updatedAt = now;
    }

    public void tune(int timeoutSeconds, int maxRetries, int breakerFailureThreshold, int breakerWindowSeconds,
                     int breakerOpenSeconds, Instant now) {
        this.timeoutSeconds = timeoutSeconds;
        this.maxRetries = maxRetries;
        this.breakerFailureThreshold = breakerFailureThreshold;
        this.breakerWindowSeconds = breakerWindowSeconds;
        this.breakerOpenSeconds = breakerOpenSeconds;
        this.updatedAt = now;
    }

    /** Đổi vai chính và dự phòng cho nhau (chuyển dự phòng bằng tay). */
    public void swap(Instant now) {
        if (fallbackProviderId == null) {
            throw new IllegalStateException("Không có nhà cung cấp dự phòng");
        }
        UUID previous = primaryProviderId;
        primaryProviderId = fallbackProviderId;
        fallbackProviderId = previous;
        updatedAt = now;
    }

    public AiCapability getCapability() {
        return capability;
    }

    public UUID getPrimaryProviderId() {
        return primaryProviderId;
    }

    public UUID getFallbackProviderId() {
        return fallbackProviderId;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public Duration timeout() {
        return Duration.ofSeconds(timeoutSeconds);
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public int getBreakerFailureThreshold() {
        return breakerFailureThreshold;
    }

    public int getBreakerWindowSeconds() {
        return breakerWindowSeconds;
    }

    public int getBreakerOpenSeconds() {
        return breakerOpenSeconds;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
