package com.learningcompanion.ai.config.application;

import com.learningcompanion.ai.config.domain.AiConfigVersion;
import com.learningcompanion.ai.config.domain.AiConfigVersionRepository;
import com.learningcompanion.ai.config.domain.AiFeature;
import com.learningcompanion.ai.config.domain.ConfigVersionStatus;
import com.learningcompanion.ai.gateway.model.ChatRequest;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đọc cấu hình đang phát hành của một chức năng (NCL-04-CN-002). Chức năng trí tuệ nhân tạo dựng yêu cầu qua
 * {@link #chatRequest} rồi mới gọi {@code AiGateway}, nên lời gọi mới luôn chạy theo phiên bản vừa phát hành, không cần
 * khởi động lại. Không có bộ nhớ đệm: phát hành hay quay lại có hiệu lực ngay ở mọi máy chủ.
 */
@Service
public class AiConfigResolver {

    /** Cấu hình một lời gọi cần; {@code versionNumber} trống khi chức năng chưa có phiên bản nào (dùng giá trị gốc). */
    public record ResolvedConfig(AiFeature feature, Integer versionNumber, String providerCode, String model,
                                 BigDecimal temperature, int maxOutputTokens, String systemPrompt) {

        public static ResolvedConfig of(AiConfigVersion version) {
            return new ResolvedConfig(version.getFeature(), version.getVersionNumber(), version.getProviderCode(),
                    version.getModel(), version.getTemperature(), version.getMaxOutputTokens(),
                    version.getSystemPrompt());
        }

        public static ResolvedConfig defaults(AiFeature feature) {
            return new ResolvedConfig(feature, null, null, null, feature.defaultTemperature(),
                    feature.defaultMaxOutputTokens(), feature.defaultSystemPrompt());
        }

        public ChatRequest toRequest(List<ChatRequest.Message> messages) {
            return new ChatRequest(systemPrompt, messages, maxOutputTokens, tuning());
        }

        /** Nhà cung cấp ưu tiên, mô hình và số phiên bản cho lời gọi không sinh văn bản (nhận dạng chữ, vector). */
        public ChatRequest.Tuning tuning() {
            return new ChatRequest.Tuning(providerCode, model, temperature.doubleValue(), versionNumber);
        }
    }

    private final AiConfigVersionRepository versions;

    public AiConfigResolver(AiConfigVersionRepository versions) {
        this.versions = versions;
    }

    @Transactional(readOnly = true)
    public ResolvedConfig resolve(AiFeature feature) {
        return versions.findByFeatureAndStatus(feature, ConfigVersionStatus.PUBLISHED)
                .map(ResolvedConfig::of)
                .orElseGet(() -> ResolvedConfig.defaults(feature));
    }

    /** Yêu cầu sinh văn bản theo cấu hình đang phát hành, cho một câu hỏi của người dùng. */
    public ChatRequest chatRequest(AiFeature feature, String userText) {
        return chatRequest(feature, List.of(new ChatRequest.Message(ChatRequest.Role.USER, userText)));
    }

    public ChatRequest chatRequest(AiFeature feature, List<ChatRequest.Message> messages) {
        return resolve(feature).toRequest(messages);
    }

    /**
     * Cấu hình đang phát hành cho lời gọi {@code AiGateway.embed} hay {@code recognizeText}: lời gọi đi tới nhà cung cấp
     * ưu tiên của phiên bản và được ghi số phiên bản trong sổ đo.
     */
    public ChatRequest.Tuning tuning(AiFeature feature) {
        return resolve(feature).tuning();
    }
}
