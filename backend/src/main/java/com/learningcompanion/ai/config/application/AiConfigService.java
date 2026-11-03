package com.learningcompanion.ai.config.application;

import com.learningcompanion.ai.config.api.dto.AiConfigDiffResponse;
import com.learningcompanion.ai.config.api.dto.AiConfigOverviewResponse;
import com.learningcompanion.ai.config.api.dto.AiConfigVersionRequest;
import com.learningcompanion.ai.config.api.dto.AiConfigVersionResponse;
import com.learningcompanion.ai.config.domain.AiConfigVersion;
import com.learningcompanion.ai.config.domain.AiConfigVersionRepository;
import com.learningcompanion.ai.config.domain.AiFeature;
import com.learningcompanion.ai.config.domain.ConfigVersionStatus;
import com.learningcompanion.ai.gateway.AiProviderConfig;
import com.learningcompanion.ai.gateway.AiProviderConfigRepository;
import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditChanges;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.web.ClientInfo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cấu hình mô hình và lời nhắc hệ thống theo phiên bản (NCL-04-CN-002, QTN-12): lưu nháp, xem khác biệt, phát hành,
 * quay lại phiên bản trước. Mỗi lần lưu tạo một phiên bản mới; phiên bản đã lưu không bao giờ bị sửa. Mỗi chức năng chỉ
 * có một phiên bản đang phát hành. Mọi thao tác được ghi nhật ký với người thực hiện, nội dung và thời điểm (TC-05);
 * nhật ký chỉ ghi tham số và số dòng thay đổi, còn toàn văn lời nhắc nằm trong lịch sử phiên bản.
 */
@Service
public class AiConfigService {

    public static final int MAX_PROMPT_LENGTH = 20000;

    private final AiConfigVersionRepository versions;
    private final AiProviderConfigRepository providers;
    private final UserAccountRepository accounts;
    private final ConfigDiffService diffs;
    private final AuditLogger audit;
    private final Clock clock;

    public AiConfigService(AiConfigVersionRepository versions, AiProviderConfigRepository providers,
                           UserAccountRepository accounts, ConfigDiffService diffs, AuditLogger audit, Clock clock) {
        this.versions = versions;
        this.providers = providers;
        this.accounts = accounts;
        this.diffs = diffs;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ đăng ký chức năng

    /**
     * Chức năng chưa có phiên bản nào thì tạo phiên bản 1 từ giá trị gốc khai trong {@link AiFeature} và phát hành
     * luôn, để chức năng chạy được ngay và có lịch sử từ đầu. Gọi khi ứng dụng khởi động.
     */
    @Transactional
    public void registerFeatures() {
        Instant now = clock.instant();
        for (AiFeature feature : AiFeature.values()) {
            if (versions.existsByFeature(feature)) {
                continue;
            }
            AiConfigVersion first = AiConfigVersion.draft(feature, 1, null, null, feature.defaultTemperature(),
                    feature.defaultMaxOutputTokens(), feature.defaultSystemPrompt(), null, null, now);
            first.publish(null, now);
            versions.saveAndFlush(first);
            audit.bySystem(AuditAction.AI_CONFIG_REGISTERED, AuditObjectType.AI_CONFIG, first.getId(),
                    Map.of("feature", feature.name(), "version", 1));
        }
    }

    // ------------------------------------------------------------------ xem

    @Transactional(readOnly = true)
    public AiConfigOverviewResponse overview() {
        List<AiConfigVersion> published = Arrays.stream(AiFeature.values())
                .map(feature -> versions.findByFeatureAndStatus(feature, ConfigVersionStatus.PUBLISHED).orElse(null))
                .filter(Objects::nonNull).toList();
        Map<UUID, UserAccount> people = people(published);
        List<AiConfigOverviewResponse.Feature> features = Arrays.stream(AiFeature.values()).map(feature -> {
            List<AiConfigVersion> all = versions.findByFeatureOrderByVersionNumberDesc(feature);
            AiConfigVersion live = all.stream().filter(v -> v.getStatus() == ConfigVersionStatus.PUBLISHED)
                    .findFirst().orElse(null);
            int drafts = (int) all.stream().filter(v -> v.getStatus() == ConfigVersionStatus.DRAFT).count();
            return new AiConfigOverviewResponse.Feature(feature, feature.capability(),
                    live == null ? null : toResponse(live, people),
                    all.isEmpty() ? 0 : all.getFirst().getVersionNumber(), drafts);
        }).toList();
        return new AiConfigOverviewResponse(features, PromptTrialSamples.SETS);
    }

    /** Lịch sử mọi phiên bản của một chức năng, mới nhất trước. */
    @Transactional(readOnly = true)
    public List<AiConfigVersionResponse> history(AiFeature feature) {
        List<AiConfigVersion> all = versions.findByFeatureOrderByVersionNumberDesc(feature);
        Map<UUID, UserAccount> people = people(all);
        return all.stream().map(version -> toResponse(version, people)).toList();
    }

    @Transactional(readOnly = true)
    public AiConfigVersionResponse version(AiFeature feature, int number) {
        AiConfigVersion version = find(feature, number);
        return toResponse(version, people(List.of(version)));
    }

    @Transactional(readOnly = true)
    public AiConfigDiffResponse diff(AiFeature feature, int from, int to) {
        AiConfigVersion before = find(feature, from);
        AiConfigVersion after = find(feature, to);
        List<AiConfigDiffResponse.FieldChange> fields = new ArrayList<>();
        addChange(fields, "providerCode", before.getProviderCode(), after.getProviderCode());
        addChange(fields, "model", before.getModel(), after.getModel());
        addChange(fields, "temperature", plain(before.getTemperature()), plain(after.getTemperature()));
        addChange(fields, "maxOutputTokens", before.getMaxOutputTokens(), after.getMaxOutputTokens());
        return new AiConfigDiffResponse(from, to, fields,
                diffs.diffLines(before.getSystemPrompt(), after.getSystemPrompt()));
    }

    // ------------------------------------------------------------------ lưu nháp

    /**
     * Lưu thành một phiên bản nháp mới. Lời nhắc chứa địa chỉ thư hay số điện thoại thì không lưu, trả vị trí từng
     * đoạn để giao diện đánh dấu (TC-03); lần bị chặn vẫn được ghi nhật ký nên giao dịch không bị hủy.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public AiConfigVersionResponse saveDraft(UUID adminId, AiFeature feature, AiConfigVersionRequest request,
                                             ClientInfo client) {
        // Quét đúng chuỗi quản trị viên gửi lên để vị trí các đoạn khớp với ô soạn thảo.
        List<PromptPiiDetector.Finding> findings = PromptPiiDetector.scan(request.systemPrompt());
        if (!findings.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("feature", feature.name());
            details.put("kinds", findings.stream().map(finding -> finding.kind().name()).distinct().sorted()
                    .toList());
            details.put("matches", findings.size());
            audit.byUser(adminId, AuditAction.AI_CONFIG_SAVE_REJECTED, AuditObjectType.AI_CONFIG, null, details,
                    client);
            throw new BusinessException(ErrorCode.AI_CONFIG_PROMPT_HAS_PII, "systemPrompt",
                    Map.of("findings", findings.stream().map(finding -> Map.of("kind", finding.kind().name(),
                            "start", finding.start(), "end", finding.end())).toList()));
        }
        String prompt = request.systemPrompt().strip();
        String providerCode = blankToNull(request.providerCode());
        String model = blankToNull(request.model());
        if (providerCode != null) {
            AiProviderConfig provider = providers.findByCode(providerCode).orElse(null);
            if (provider == null || !provider.supports(feature.capability())) {
                throw new BusinessException(ErrorCode.AI_CONFIG_PROVIDER_INVALID, "providerCode");
            }
        } else if (model != null) {
            // Tên mô hình riêng của từng nhà cung cấp: không chọn nhà cung cấp thì không ghi được mô hình.
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "model");
        }

        List<AiConfigVersion> existing = versions.lockFeature(feature);
        int number = existing.isEmpty() ? 1 : existing.getFirst().getVersionNumber() + 1;
        AiConfigVersion base = request.basedOnVersion() == null ? null : existing.stream()
                .filter(version -> version.getVersionNumber() == request.basedOnVersion()).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "basedOnVersion"));
        AiConfigVersion draft = AiConfigVersion.draft(feature, number, providerCode, model,
                request.temperature().setScale(2, RoundingMode.HALF_UP), request.maxOutputTokens(), prompt,
                base == null ? null : base.getVersionNumber(), adminId, clock.instant());
        versions.save(draft);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("feature", feature.name());
        details.put("version", number);
        details.put("basedOnVersion", base == null ? null : base.getVersionNumber());
        if (base != null) {
            details.putAll(AuditChanges.of()
                    .field("providerCode", base.getProviderCode(), providerCode)
                    .field("model", base.getModel(), model)
                    .field("temperature", plain(base.getTemperature()), plain(draft.getTemperature()))
                    .field("maxOutputTokens", base.getMaxOutputTokens(), draft.getMaxOutputTokens())
                    .toDetails());
            List<ConfigDiffService.Line> lines = diffs.diffLines(base.getSystemPrompt(), prompt);
            details.put("instructionLinesAdded",
                    lines.stream().filter(line -> line.change() == ConfigDiffService.LineChange.ADDED).count());
            details.put("instructionLinesRemoved",
                    lines.stream().filter(line -> line.change() == ConfigDiffService.LineChange.REMOVED).count());
        }
        details.put("instructionsSha256", sha256(prompt));
        audit.byUser(adminId, AuditAction.AI_CONFIG_DRAFT_SAVED, AuditObjectType.AI_CONFIG, draft.getId(), details,
                client);
        return toResponse(draft, people(List.of(draft)));
    }

    // ------------------------------------------------------------------ thử nghiệm

    /** Ghi lại phiên bản vừa được thử thành công với câu hỏi mẫu; phiên bản nháp chỉ phát hành được sau khi thử. */
    @Transactional
    public void recordTrial(AiFeature feature, int number) {
        find(feature, number).recordTrial(clock.instant());
    }

    // ------------------------------------------------------------------ phát hành, quay lại

    /**
     * Phát hành một phiên bản nháp (đã thử) hoặc phát hành lại một phiên bản từng chạy. Phiên bản đang chạy chuyển sang
     * đã được thay; lời gọi mới của chức năng dùng phiên bản này ngay (TC-01).
     */
    @Transactional
    public AiConfigVersionResponse publish(UUID adminId, AiFeature feature, int number, ClientInfo client) {
        List<AiConfigVersion> all = versions.lockFeature(feature);
        AiConfigVersion target = all.stream().filter(version -> version.getVersionNumber() == number).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!target.isPublishable()) {
            throw new BusinessException(ErrorCode.AI_CONFIG_NOT_PUBLISHABLE);
        }
        if (target.getStatus() == ConfigVersionStatus.DRAFT && target.getLastTrialAt() == null) {
            throw new BusinessException(ErrorCode.AI_CONFIG_TRIAL_REQUIRED);
        }
        requireUsableProvider(target);
        AiConfigVersion current = published(all);
        boolean republished = target.getPublishedAt() != null;
        Instant now = clock.instant();
        if (current != null) {
            current.supersede();
            // Chỉ mục duy nhất cho phiên bản đang phát hành: hạ phiên bản cũ xuống trước khi nâng phiên bản mới.
            versions.saveAndFlush(current);
        }
        target.publish(adminId, now);
        versions.saveAndFlush(target);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("feature", feature.name());
        details.put("version", number);
        details.put("previousVersion", current == null ? null : current.getVersionNumber());
        details.put("republished", republished);
        audit.byUser(adminId, AuditAction.AI_CONFIG_PUBLISHED, AuditObjectType.AI_CONFIG, target.getId(), details,
                client);
        return toResponse(target, people(List.of(target)));
    }

    /**
     * Quay lại phiên bản phát hành gần nhất trước phiên bản đang chạy chỉ bằng một thao tác (TC-02): phiên bản đang
     * chạy bị thu hồi, phiên bản trước được phát hành lại ngay.
     */
    @Transactional
    public AiConfigVersionResponse rollback(UUID adminId, AiFeature feature, ClientInfo client) {
        List<AiConfigVersion> all = versions.lockFeature(feature);
        AiConfigVersion current = published(all);
        AiConfigVersion previous = versions.previouslyPublished(feature).stream().findFirst().orElse(null);
        if (current == null || previous == null) {
            throw new BusinessException(ErrorCode.AI_CONFIG_NO_PREVIOUS_VERSION);
        }
        Instant now = clock.instant();
        current.revoke(adminId, now);
        versions.saveAndFlush(current);
        previous.publish(adminId, now);
        versions.saveAndFlush(previous);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("feature", feature.name());
        details.put("fromVersion", current.getVersionNumber());
        details.put("toVersion", previous.getVersionNumber());
        audit.byUser(adminId, AuditAction.AI_CONFIG_ROLLED_BACK, AuditObjectType.AI_CONFIG, previous.getId(), details,
                client);
        return toResponse(previous, people(List.of(previous)));
    }

    // ------------------------------------------------------------------ hỗ trợ

    /** Nhà cung cấp ghi trong phiên bản phải đang bật và đã xác nhận không huấn luyện thì mới cho chạy với người học. */
    private void requireUsableProvider(AiConfigVersion version) {
        if (version.getProviderCode() == null) {
            return;
        }
        AiProviderConfig provider = providers.findByCode(version.getProviderCode()).orElse(null);
        if (provider == null || !provider.isEnabled() || !provider.isNoTrainingConfirmed()
                || !provider.supports(version.getFeature().capability())) {
            throw new BusinessException(ErrorCode.AI_CONFIG_PROVIDER_UNAVAILABLE, null,
                    Map.of("providerCode", version.getProviderCode()));
        }
    }

    AiConfigVersion find(AiFeature feature, int number) {
        return versions.findByFeatureAndVersionNumber(feature, number)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private static AiConfigVersion published(List<AiConfigVersion> all) {
        return all.stream().filter(version -> version.getStatus() == ConfigVersionStatus.PUBLISHED).findFirst()
                .orElse(null);
    }

    private Map<UUID, UserAccount> people(List<AiConfigVersion> list) {
        Set<UUID> ids = list.stream()
                .flatMap(version -> Stream.of(version.getCreatedBy(), version.getPublishedBy(),
                        version.getRevokedBy()))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return accounts.findAllById(ids).stream().collect(Collectors.toMap(UserAccount::getId, Function.identity()));
    }

    private static AiConfigVersionResponse toResponse(AiConfigVersion version, Map<UUID, UserAccount> people) {
        return new AiConfigVersionResponse(version.getId(), version.getFeature(), version.getVersionNumber(),
                version.getStatus(), version.getProviderCode(), version.getModel(), version.getTemperature(),
                version.getMaxOutputTokens(), version.getSystemPrompt(), version.getBasedOnVersion(),
                version.getCreatedAt(), actor(version.getCreatedBy(), people), version.getLastTrialAt(),
                version.getPublishedAt(), actor(version.getPublishedBy(), people), version.getRevokedAt(),
                actor(version.getRevokedBy(), people));
    }

    private static AiConfigVersionResponse.Actor actor(UUID id, Map<UUID, UserAccount> people) {
        if (id == null) {
            return null;
        }
        UserAccount account = people.get(id);
        return new AiConfigVersionResponse.Actor(id, account == null ? null : account.getDisplayName());
    }

    private static void addChange(List<AiConfigDiffResponse.FieldChange> into, String field, Object before,
                                  Object after) {
        if (!Objects.equals(before, after)) {
            into.add(new AiConfigDiffResponse.FieldChange(field, before, after));
        }
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Dấu vân tay ngắn của lời nhắc để đối chiếu nhật ký với lịch sử phiên bản mà không chép nội dung. */
    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
