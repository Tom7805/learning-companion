package com.learningcompanion.ai.config.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Một phiên bản cấu hình của một chức năng trí tuệ nhân tạo (NCL-04-CN-002): nhà cung cấp, mô hình, nhiệt độ, độ dài
 * tối đa và lời nhắc hệ thống. Nội dung cấu hình không bao giờ đổi sau khi lưu; chỉ trạng thái và các mốc phát hành,
 * thu hồi, thử nghiệm được ghi thêm.
 */
@Entity
@Table(name = "ai_config_versions")
public class AiConfigVersion {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private AiFeature feature;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConfigVersionStatus status;

    @Column(name = "provider_code", length = 40)
    private String providerCode;

    @Column(length = 120)
    private String model;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal temperature;

    @Column(name = "max_output_tokens", nullable = false)
    private int maxOutputTokens;

    @Column(name = "system_prompt", nullable = false, columnDefinition = "text")
    private String systemPrompt;

    @Column(name = "based_on_version")
    private Integer basedOnVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "last_trial_at")
    private Instant lastTrialAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by")
    private UUID publishedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @Version
    private long version;

    protected AiConfigVersion() {
    }

    private AiConfigVersion(AiFeature feature, int versionNumber, String providerCode, String model,
                            BigDecimal temperature, int maxOutputTokens, String systemPrompt, Integer basedOnVersion,
                            UUID createdBy, Instant now) {
        this.id = UUID.randomUUID();
        this.feature = feature;
        this.versionNumber = versionNumber;
        this.status = ConfigVersionStatus.DRAFT;
        this.providerCode = providerCode;
        this.model = model;
        this.temperature = temperature;
        this.maxOutputTokens = maxOutputTokens;
        this.systemPrompt = systemPrompt;
        this.basedOnVersion = basedOnVersion;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    /** Phiên bản nháp mới; {@code createdBy} trống nghĩa là hệ thống tạo. */
    public static AiConfigVersion draft(AiFeature feature, int versionNumber, String providerCode, String model,
                                        BigDecimal temperature, int maxOutputTokens, String systemPrompt,
                                        Integer basedOnVersion, UUID createdBy, Instant now) {
        return new AiConfigVersion(feature, versionNumber, providerCode, model, temperature, maxOutputTokens,
                systemPrompt, basedOnVersion, createdBy, now);
    }

    public void publish(UUID by, Instant now) {
        this.status = ConfigVersionStatus.PUBLISHED;
        this.publishedBy = by;
        this.publishedAt = now;
    }

    /** Bản khác vừa được phát hành thay; mốc phát hành cũ được giữ để biết thứ tự khi quay lại. */
    public void supersede() {
        this.status = ConfigVersionStatus.SUPERSEDED;
    }

    public void revoke(UUID by, Instant now) {
        this.status = ConfigVersionStatus.REVOKED;
        this.revokedBy = by;
        this.revokedAt = now;
    }

    public void recordTrial(Instant now) {
        this.lastTrialAt = now;
    }

    public boolean isPublishable() {
        return status == ConfigVersionStatus.DRAFT || status == ConfigVersionStatus.SUPERSEDED;
    }

    public UUID getId() {
        return id;
    }

    public AiFeature getFeature() {
        return feature;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public ConfigVersionStatus getStatus() {
        return status;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public String getModel() {
        return model;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public Integer getBasedOnVersion() {
        return basedOnVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getLastTrialAt() {
        return lastTrialAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public UUID getPublishedBy() {
        return publishedBy;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getRevokedBy() {
        return revokedBy;
    }
}
