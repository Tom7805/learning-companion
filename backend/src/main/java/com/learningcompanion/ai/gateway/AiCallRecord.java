package com.learningcompanion.ai.gateway;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Một lời gọi qua lớp kết nối: khả năng, chức năng gọi, nhà cung cấp, số đơn vị, độ trễ và chi phí. Cố ý không có cột
 * nào chứa nội dung lời gọi hay kết quả (NCL-04-CN-001 TC-01).
 */
@Entity
@Immutable
@Table(name = "ai_call_records")
public class AiCallRecord {

    public enum Outcome { SUCCESS, FAILURE, TIMEOUT, UNAVAILABLE }

    @Id
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiCapability capability;

    @Column(nullable = false, length = 60)
    private String feature;

    @Column(name = "provider_code", length = 40)
    private String providerCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Outcome outcome;

    @Column(name = "used_fallback", nullable = false)
    private boolean usedFallback;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "input_units", nullable = false)
    private int inputUnits;

    @Column(name = "output_units", nullable = false)
    private int outputUnits;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(name = "cost_micro_usd", nullable = false)
    private long costMicroUsd;

    @Column(name = "error_code", length = 40)
    private String errorCode;

    protected AiCallRecord() {
    }

    public AiCallRecord(Instant occurredAt, AiCapability capability, String feature, String providerCode,
                        Outcome outcome, boolean usedFallback, int attempts, int inputUnits, int outputUnits,
                        int latencyMs, long costMicroUsd, String errorCode) {
        this.id = UUID.randomUUID();
        this.occurredAt = occurredAt;
        this.capability = capability;
        this.feature = feature;
        this.providerCode = providerCode;
        this.outcome = outcome;
        this.usedFallback = usedFallback;
        this.attempts = attempts;
        this.inputUnits = inputUnits;
        this.outputUnits = outputUnits;
        this.latencyMs = latencyMs;
        this.costMicroUsd = costMicroUsd;
        this.errorCode = errorCode;
    }

    public UUID getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public AiCapability getCapability() {
        return capability;
    }

    public String getFeature() {
        return feature;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public boolean isUsedFallback() {
        return usedFallback;
    }

    public int getAttempts() {
        return attempts;
    }

    public int getInputUnits() {
        return inputUnits;
    }

    public int getOutputUnits() {
        return outputUnits;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public long getCostMicroUsd() {
        return costMicroUsd;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
