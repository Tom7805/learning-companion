package com.learningcompanion.ai.gateway;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Cảnh báo cho quản trị viên: mạch của một nhà cung cấp vừa ngắt và lời gọi chuyển sang dự phòng (TC-02). */
@Entity
@Table(name = "ai_gateway_alerts")
public class AiGatewayAlert {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiCapability capability;

    @Column(name = "provider_code", nullable = false, length = 40)
    private String providerCode;

    @Column(name = "fallback_provider_code", length = 40)
    private String fallbackProviderCode;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    protected AiGatewayAlert() {
    }

    public AiGatewayAlert(AiCapability capability, String providerCode, String fallbackProviderCode,
                          Instant openedAt) {
        this.id = UUID.randomUUID();
        this.capability = capability;
        this.providerCode = providerCode;
        this.fallbackProviderCode = fallbackProviderCode;
        this.openedAt = openedAt;
    }

    public void acknowledge(UUID adminId, Instant now) {
        if (acknowledgedAt == null) {
            acknowledgedAt = now;
            acknowledgedBy = adminId;
        }
    }

    public UUID getId() {
        return id;
    }

    public AiCapability getCapability() {
        return capability;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public String getFallbackProviderCode() {
        return fallbackProviderCode;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }
}
