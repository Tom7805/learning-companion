package com.learningcompanion.ai.gateway;

import com.learningcompanion.ai.provider.AdapterType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Một nhà cung cấp dịch vụ trí tuệ nhân tạo đã khai báo (NCL-04-CN-001). Khóa bí mật không nằm ở đây: chỉ lưu tên khóa
 * trong kho bí mật. Chỉ bật được khi đã xác nhận cam kết không dùng dữ liệu để huấn luyện (QTN-11).
 */
@Entity
@Table(name = "ai_providers")
public class AiProviderConfig {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdapterType adapter;

    @Column(name = "base_url", length = 300)
    private String baseUrl;

    @Column(name = "text_model", length = 120)
    private String textModel;

    @Column(name = "embedding_model", length = 120)
    private String embeddingModel;

    @Column(name = "vision_model", length = 120)
    private String visionModel;

    @Column(nullable = false, length = 200)
    private String capabilities;

    @Column(name = "secret_name", length = 120)
    private String secretName;

    @Column(name = "input_price_per_million", nullable = false, precision = 12, scale = 4)
    private BigDecimal inputPricePerMillion;

    @Column(name = "output_price_per_million", nullable = false, precision = 12, scale = 4)
    private BigDecimal outputPricePerMillion;

    @Column(name = "no_training_confirmed_at")
    private Instant noTrainingConfirmedAt;

    @Column(name = "no_training_confirmed_by")
    private UUID noTrainingConfirmedBy;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected AiProviderConfig() {
    }

    public static AiProviderConfig declare(String code, AdapterType adapter, Instant now) {
        AiProviderConfig provider = new AiProviderConfig();
        provider.id = UUID.randomUUID();
        provider.code = code;
        provider.adapter = adapter;
        provider.inputPricePerMillion = BigDecimal.ZERO;
        provider.outputPricePerMillion = BigDecimal.ZERO;
        provider.capabilities = "";
        provider.createdAt = now;
        provider.updatedAt = now;
        return provider;
    }

    public void describe(String displayName, String baseUrl, String textModel, String embeddingModel,
                         String visionModel, Set<AiCapability> capabilities, String secretName,
                         BigDecimal inputPricePerMillion, BigDecimal outputPricePerMillion, Instant now) {
        this.displayName = displayName;
        this.baseUrl = baseUrl;
        this.textModel = textModel;
        this.embeddingModel = embeddingModel;
        this.visionModel = visionModel;
        this.capabilities = capabilities.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
        this.secretName = secretName;
        this.inputPricePerMillion = inputPricePerMillion;
        this.outputPricePerMillion = outputPricePerMillion;
        this.updatedAt = now;
    }

    public void confirmNoTraining(UUID adminId, Instant now) {
        if (noTrainingConfirmedAt == null) {
            noTrainingConfirmedAt = now;
            noTrainingConfirmedBy = adminId;
            updatedAt = now;
        }
    }

    /** Rút lại xác nhận: nhà cung cấp cũng bị tắt luôn, vì không còn đủ điều kiện chạy. */
    public void withdrawNoTraining(Instant now) {
        if (noTrainingConfirmedAt != null) {
            noTrainingConfirmedAt = null;
            noTrainingConfirmedBy = null;
            enabled = false;
            updatedAt = now;
        }
    }

    public void setEnabled(boolean enabled, Instant now) {
        this.enabled = enabled;
        this.updatedAt = now;
    }

    public boolean supports(AiCapability capability) {
        return capabilities().contains(capability) && adapter.supports(capability);
    }

    public Set<AiCapability> capabilities() {
        if (capabilities == null || capabilities.isBlank()) {
            return EnumSet.noneOf(AiCapability.class);
        }
        return Arrays.stream(capabilities.split(",")).map(AiCapability::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AiCapability.class)));
    }

    /** Mô hình dùng cho một khả năng; bản mô phỏng không cần. */
    public String modelFor(AiCapability capability) {
        return switch (capability) {
            case TEXT_GENERATION -> textModel;
            case EMBEDDING -> embeddingModel;
            case VISION, OCR -> visionModel;
        };
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AdapterType getAdapter() {
        return adapter;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getTextModel() {
        return textModel;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public String getVisionModel() {
        return visionModel;
    }

    public String getSecretName() {
        return secretName;
    }

    public BigDecimal getInputPricePerMillion() {
        return inputPricePerMillion;
    }

    public BigDecimal getOutputPricePerMillion() {
        return outputPricePerMillion;
    }

    public Instant getNoTrainingConfirmedAt() {
        return noTrainingConfirmedAt;
    }

    public boolean isNoTrainingConfirmed() {
        return noTrainingConfirmedAt != null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
