package com.learningcompanion.ai.gateway;

import com.learningcompanion.ai.gateway.api.dto.AiGatewayOverviewResponse;
import com.learningcompanion.ai.gateway.api.dto.AiProviderRequest;
import com.learningcompanion.ai.gateway.api.dto.AiProviderResponse;
import com.learningcompanion.ai.gateway.api.dto.AiRouteRequest;
import com.learningcompanion.ai.gateway.api.dto.AiRouteResponse;
import com.learningcompanion.ai.gateway.api.dto.AiUsageResponse;
import com.learningcompanion.ai.provider.AdapterType;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditChanges;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.web.ClientInfo;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản trị lớp kết nối (NCL-04-CN-001): khai báo nhà cung cấp, bật tắt, định tuyến từng khả năng, chuyển dự phòng và
 * xem mức sử dụng. Mọi thay đổi được ghi nhật ký kèm người thực hiện, nội dung và thời điểm (TC-06).
 */
@Service
public class AiGatewayAdminService {

    public static final int RECENT_CALLS = 20;
    public static final int MAX_USAGE_DAYS = 90;

    private final AiProviderConfigRepository providers;
    private final AiCapabilityRouteRepository routes;
    private final AiCallRecordRepository calls;
    private final AiGatewayAlertRepository alerts;
    private final CircuitBreakerRegistry breakers;
    private final AiSecretStore secrets;
    private final AuditLogger audit;
    private final Clock clock;

    public AiGatewayAdminService(AiProviderConfigRepository providers, AiCapabilityRouteRepository routes,
                                 AiCallRecordRepository calls, AiGatewayAlertRepository alerts,
                                 CircuitBreakerRegistry breakers, AiSecretStore secrets, AuditLogger audit,
                                 Clock clock) {
        this.providers = providers;
        this.routes = routes;
        this.calls = calls;
        this.alerts = alerts;
        this.breakers = breakers;
        this.secrets = secrets;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ xem

    @Transactional(readOnly = true)
    public AiGatewayOverviewResponse overview() {
        List<AiProviderConfig> all = providers.findAllByOrderByCreatedAtAscCodeAsc();
        Map<UUID, AiProviderConfig> byId = all.stream()
                .collect(Collectors.toMap(AiProviderConfig::getId, Function.identity()));
        List<AiCapabilityRoute> allRoutes = routes.findAll().stream()
                .sorted(Comparator.comparing(AiCapabilityRoute::getCapability)).toList();
        List<AiRouteResponse> routeViews = allRoutes.stream().map(route -> toResponse(route, byId)).toList();
        List<AiProviderResponse> providerViews = all.stream().map(provider -> toResponse(provider, allRoutes)).toList();
        List<AiGatewayOverviewResponse.Alert> openAlerts = alerts.findByAcknowledgedAtIsNullOrderByOpenedAtDesc()
                .stream().map(alert -> new AiGatewayOverviewResponse.Alert(alert.getId(), alert.getCapability(),
                        alert.getProviderCode(), alert.getFallbackProviderCode(), alert.getOpenedAt()))
                .toList();
        return new AiGatewayOverviewResponse(routeViews, providerViews, openAlerts);
    }

    @Transactional(readOnly = true)
    public AiUsageResponse usage(int days) {
        int span = Math.clamp(days, 1, MAX_USAGE_DAYS);
        Instant since = clock.instant().minus(Duration.ofDays(span));
        List<AiUsageResponse.Row> rows = calls.summarizeSince(since).stream()
                .map(row -> new AiUsageResponse.Row(row.getCapability(), row.getProviderCode(), row.getCalls(),
                        row.getFailures(), row.getFallbackCalls(), row.getInputUnits(), row.getOutputUnits(),
                        row.getCostMicroUsd(),
                        row.getAverageLatencyMs() == null ? 0 : Math.round(row.getAverageLatencyMs())))
                .toList();
        AiUsageResponse.Totals totals = new AiUsageResponse.Totals(
                rows.stream().mapToLong(AiUsageResponse.Row::calls).sum(),
                rows.stream().mapToLong(AiUsageResponse.Row::failures).sum(),
                rows.stream().mapToLong(AiUsageResponse.Row::inputUnits).sum(),
                rows.stream().mapToLong(AiUsageResponse.Row::outputUnits).sum(),
                rows.stream().mapToLong(AiUsageResponse.Row::costMicroUsd).sum());
        List<AiUsageResponse.Call> recent = calls.findByOrderByOccurredAtDesc(PageRequest.of(0, RECENT_CALLS))
                .stream().map(call -> new AiUsageResponse.Call(call.getId(), call.getOccurredAt(),
                        call.getCapability(), call.getFeature(), call.getProviderCode(), call.getOutcome().name(),
                        call.isUsedFallback(), call.getAttempts(), call.getInputUnits(), call.getOutputUnits(),
                        call.getLatencyMs(), call.getCostMicroUsd(), call.getErrorCode()))
                .toList();
        return new AiUsageResponse(since, totals, rows, recent);
    }

    // ------------------------------------------------------------------ nhà cung cấp

    @Transactional
    public AiProviderResponse create(UUID adminId, AiProviderRequest request, ClientInfo client) {
        if (request.code() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "code");
        }
        if (providers.existsByCode(request.code())) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_CODE_TAKEN, "code");
        }
        Instant now = clock.instant();
        AiProviderConfig provider = AiProviderConfig.declare(request.code(), request.adapter(), now);
        apply(provider, request, adminId, now);
        providers.save(provider);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("code", provider.getCode());
        details.put("adapter", provider.getAdapter().name());
        details.put("capabilities", names(provider.capabilities()));
        details.put("noTrainingConfirmed", provider.isNoTrainingConfirmed());
        audit.byUser(adminId, AuditAction.AI_PROVIDER_CREATED, AuditObjectType.AI_PROVIDER, provider.getId(),
                details, client);
        return toResponse(provider, routes.findAll());
    }

    @Transactional
    public AiProviderResponse update(UUID adminId, UUID providerId, AiProviderRequest request, ClientInfo client) {
        AiProviderConfig provider = find(providerId);
        if (request.adapter() != provider.getAdapter()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "adapter");
        }
        List<AiCapabilityRoute> using = routes.findUsing(providerId);
        // Nhà cung cấp đang chạy trong định tuyến phải giữ đủ điều kiện: không rút xác nhận, không bỏ khả năng đang dùng.
        if (!using.isEmpty() && provider.isEnabled()) {
            boolean dropsUsedCapability = using.stream()
                    .anyMatch(route -> !request.capabilities().contains(route.getCapability()));
            if (!request.noTrainingConfirmed() || dropsUsedCapability) {
                throw new BusinessException(ErrorCode.AI_PROVIDER_IN_USE);
            }
        }
        AuditChanges changes = AuditChanges.of()
                .field("displayName", provider.getDisplayName(), request.displayName())
                .field("baseUrl", provider.getBaseUrl(), blankToNull(request.baseUrl()))
                .field("textModel", provider.getTextModel(), blankToNull(request.textModel()))
                .field("embeddingModel", provider.getEmbeddingModel(), blankToNull(request.embeddingModel()))
                .field("visionModel", provider.getVisionModel(), blankToNull(request.visionModel()))
                .field("capabilities", names(provider.capabilities()), names(request.capabilities()))
                .field("credentialName", provider.getSecretName(), blankToNull(request.secretName()))
                .field("inputPricePerMillion", provider.getInputPricePerMillion().stripTrailingZeros().toPlainString(),
                        request.inputPricePerMillion().stripTrailingZeros().toPlainString())
                .field("outputPricePerMillion",
                        provider.getOutputPricePerMillion().stripTrailingZeros().toPlainString(),
                        request.outputPricePerMillion().stripTrailingZeros().toPlainString())
                .field("noTrainingConfirmed", provider.isNoTrainingConfirmed(), request.noTrainingConfirmed());
        Instant now = clock.instant();
        apply(provider, request, adminId, now);
        if (!changes.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>(changes.toDetails());
            details.put("code", provider.getCode());
            audit.byUser(adminId, AuditAction.AI_PROVIDER_UPDATED, AuditObjectType.AI_PROVIDER, provider.getId(),
                    details, client);
        }
        return toResponse(provider, routes.findAll());
    }

    /**
     * Bật nhà cung cấp. Thiếu xác nhận không huấn luyện (TC-04), thiếu khóa hay thiếu cấu hình thì từ chối, nêu điều
     * kiện còn thiếu và ghi lần từ chối vào nhật ký (giao dịch không bị hủy để dòng nhật ký được giữ).
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public AiProviderResponse enable(UUID adminId, UUID providerId, ClientInfo client) {
        AiProviderConfig provider = find(providerId);
        List<String> missing = missingRequirements(provider);
        if (!missing.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("code", provider.getCode());
            details.put("missing", missing);
            audit.byUser(adminId, AuditAction.AI_PROVIDER_ENABLE_REJECTED, AuditObjectType.AI_PROVIDER,
                    provider.getId(), details, client);
            ErrorCode code = switch (missing.getFirst()) {
                case "NO_TRAINING_CONFIRMATION" -> ErrorCode.AI_PROVIDER_TRAINING_NOT_CONFIRMED;
                case "SECRET" -> ErrorCode.AI_PROVIDER_SECRET_MISSING;
                default -> ErrorCode.AI_PROVIDER_INCOMPLETE;
            };
            throw new BusinessException(code, null, Map.of("missing", missing));
        }
        if (!provider.isEnabled()) {
            provider.setEnabled(true, clock.instant());
            audit.byUser(adminId, AuditAction.AI_PROVIDER_ENABLED, AuditObjectType.AI_PROVIDER, provider.getId(),
                    Map.of("code", provider.getCode()), client);
        }
        return toResponse(provider, routes.findAll());
    }

    @Transactional
    public AiProviderResponse disable(UUID adminId, UUID providerId, ClientInfo client) {
        AiProviderConfig provider = find(providerId);
        List<AiCapabilityRoute> all = routes.findAll();
        if (all.stream().anyMatch(route -> providerId.equals(route.getPrimaryProviderId()))) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_IN_USE);
        }
        if (provider.isEnabled()) {
            provider.setEnabled(false, clock.instant());
            audit.byUser(adminId, AuditAction.AI_PROVIDER_DISABLED, AuditObjectType.AI_PROVIDER, provider.getId(),
                    Map.of("code", provider.getCode()), client);
        }
        return toResponse(provider, all);
    }

    // ------------------------------------------------------------------ định tuyến

    @Transactional
    public AiRouteResponse updateRoute(UUID adminId, AiCapability capability, AiRouteRequest request,
                                       ClientInfo client) {
        AiCapabilityRoute route = routes.findById(capability)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        AiProviderConfig primary = providers.findById(request.primaryProviderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_ROUTE_INVALID, "primaryProviderId"));
        AiProviderConfig fallback = request.fallbackProviderId() == null ? null
                : providers.findById(request.fallbackProviderId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.AI_ROUTE_INVALID, "fallbackProviderId"));
        if (!routable(primary, capability)) {
            throw new BusinessException(ErrorCode.AI_ROUTE_INVALID, "primaryProviderId");
        }
        if (fallback != null && (fallback.getId().equals(primary.getId()) || !routable(fallback, capability))) {
            throw new BusinessException(ErrorCode.AI_ROUTE_INVALID, "fallbackProviderId");
        }
        Map<UUID, AiProviderConfig> byId = providersById();
        AuditChanges changes = AuditChanges.of()
                .field("primaryProvider", codeOf(byId, route.getPrimaryProviderId()), primary.getCode())
                .field("fallbackProvider", codeOf(byId, route.getFallbackProviderId()),
                        fallback == null ? null : fallback.getCode())
                .field("timeoutSeconds", route.getTimeoutSeconds(), request.timeoutSeconds())
                .field("maxRetries", route.getMaxRetries(), request.maxRetries())
                .field("breakerFailureThreshold", route.getBreakerFailureThreshold(),
                        request.breakerFailureThreshold())
                .field("breakerWindowSeconds", route.getBreakerWindowSeconds(), request.breakerWindowSeconds())
                .field("breakerOpenSeconds", route.getBreakerOpenSeconds(), request.breakerOpenSeconds());
        Instant now = clock.instant();
        route.route(primary.getId(), fallback == null ? null : fallback.getId(), now);
        route.tune(request.timeoutSeconds(), request.maxRetries(), request.breakerFailureThreshold(),
                request.breakerWindowSeconds(), request.breakerOpenSeconds(), now);
        if (!changes.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>(changes.toDetails());
            details.put("capability", capability.name());
            audit.byUser(adminId, AuditAction.AI_ROUTE_CHANGED, AuditObjectType.AI_ROUTE, null, details, client);
        }
        return toResponse(route, providersById());
    }

    /** Chuyển dự phòng bằng tay: dự phòng thành chính, chính thành dự phòng; mạch của khả năng được đóng lại. */
    @Transactional
    public AiRouteResponse switchToFallback(UUID adminId, AiCapability capability, ClientInfo client) {
        AiCapabilityRoute route = routes.findById(capability)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Map<UUID, AiProviderConfig> byId = providersById();
        AiProviderConfig fallback = route.getFallbackProviderId() == null ? null
                : byId.get(route.getFallbackProviderId());
        if (fallback == null || !routable(fallback, capability)) {
            throw new BusinessException(ErrorCode.AI_NO_FALLBACK);
        }
        String previousPrimary = codeOf(byId, route.getPrimaryProviderId());
        route.swap(clock.instant());
        breakers.reset(capability);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("capability", capability.name());
        details.put("fromProvider", previousPrimary);
        details.put("toProvider", fallback.getCode());
        audit.byUser(adminId, AuditAction.AI_FAILOVER_SWITCHED, AuditObjectType.AI_ROUTE, null, details, client);
        return toResponse(route, byId);
    }

    @Transactional
    public void acknowledgeAlert(UUID adminId, UUID alertId, ClientInfo client) {
        AiGatewayAlert alert = alerts.findById(alertId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (alert.getAcknowledgedAt() != null) {
            return;
        }
        alert.acknowledge(adminId, clock.instant());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("capability", alert.getCapability().name());
        details.put("provider", alert.getProviderCode());
        audit.byUser(adminId, AuditAction.AI_ALERT_ACKNOWLEDGED, AuditObjectType.AI_PROVIDER,
                providers.findByCode(alert.getProviderCode()).map(AiProviderConfig::getId).orElse(null), details,
                client);
    }

    // ------------------------------------------------------------------ hỗ trợ

    private void apply(AiProviderConfig provider, AiProviderRequest request, UUID adminId, Instant now) {
        Set<AiCapability> capabilities = EnumSet.noneOf(AiCapability.class);
        capabilities.addAll(request.capabilities());
        if (!request.adapter().supported().containsAll(capabilities)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "capabilities");
        }
        provider.describe(request.displayName().trim(), blankToNull(request.baseUrl()),
                blankToNull(request.textModel()), blankToNull(request.embeddingModel()),
                blankToNull(request.visionModel()), capabilities, blankToNull(request.secretName()),
                request.inputPricePerMillion(), request.outputPricePerMillion(), now);
        if (request.noTrainingConfirmed()) {
            provider.confirmNoTraining(adminId, now);
        } else {
            provider.withdrawNoTraining(now);
        }
    }

    /** Điều kiện còn thiếu để bật, theo thứ tự ưu tiên báo cho quản trị viên. */
    List<String> missingRequirements(AiProviderConfig provider) {
        List<String> missing = new ArrayList<>();
        if (!provider.isNoTrainingConfirmed()) {
            missing.add("NO_TRAINING_CONFIRMATION");
        }
        if (provider.getAdapter().requiresSecret() && !secrets.has(provider.getSecretName())) {
            missing.add("SECRET");
        }
        if (provider.getAdapter() == AdapterType.OPENAI_COMPATIBLE && provider.getBaseUrl() == null) {
            missing.add("BASE_URL");
        }
        if (provider.getAdapter() != AdapterType.MOCK && provider.capabilities().stream()
                .anyMatch(capability -> provider.modelFor(capability) == null)) {
            missing.add("MODEL");
        }
        return missing;
    }

    private static boolean routable(AiProviderConfig provider, AiCapability capability) {
        return provider.isEnabled() && provider.isNoTrainingConfirmed() && provider.supports(capability);
    }

    private AiProviderConfig find(UUID id) {
        return providers.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private Map<UUID, AiProviderConfig> providersById() {
        return providers.findAll().stream().collect(Collectors.toMap(AiProviderConfig::getId, Function.identity()));
    }

    private static String codeOf(Map<UUID, AiProviderConfig> byId, UUID id) {
        AiProviderConfig provider = id == null ? null : byId.get(id);
        return provider == null ? null : provider.getCode();
    }

    private static List<String> names(Set<AiCapability> capabilities) {
        return capabilities.stream().sorted().map(Enum::name).toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AiRouteResponse toResponse(AiCapabilityRoute route, Map<UUID, AiProviderConfig> byId) {
        Duration openFor = Duration.ofSeconds(route.getBreakerOpenSeconds());
        return new AiRouteResponse(route.getCapability(),
                endpoint(route.getCapability(), byId.get(route.getPrimaryProviderId()), openFor),
                route.getFallbackProviderId() == null ? null
                        : endpoint(route.getCapability(), byId.get(route.getFallbackProviderId()), openFor),
                route.getTimeoutSeconds(), route.getMaxRetries(), route.getBreakerFailureThreshold(),
                route.getBreakerWindowSeconds(), route.getBreakerOpenSeconds(), route.getUpdatedAt());
    }

    private AiRouteResponse.Endpoint endpoint(AiCapability capability, AiProviderConfig provider, Duration openFor) {
        if (provider == null) {
            return null;
        }
        CircuitBreakerRegistry.Snapshot circuit = breakers.snapshot(capability, provider.getCode(), openFor);
        return new AiRouteResponse.Endpoint(provider.getId(), provider.getCode(), provider.getDisplayName(),
                provider.isEnabled(), circuit.state().name(), circuit.recentFailures(), circuit.openedAt(),
                circuit.retryAt());
    }

    private AiProviderResponse toResponse(AiProviderConfig provider, List<AiCapabilityRoute> allRoutes) {
        List<AiCapability> primaryFor = allRoutes.stream()
                .filter(route -> provider.getId().equals(route.getPrimaryProviderId()))
                .map(AiCapabilityRoute::getCapability).sorted().toList();
        List<AiCapability> fallbackFor = allRoutes.stream()
                .filter(route -> provider.getId().equals(route.getFallbackProviderId()))
                .map(AiCapabilityRoute::getCapability).sorted().toList();
        return new AiProviderResponse(provider.getId(), provider.getCode(), provider.getDisplayName(),
                provider.getAdapter().name(), provider.getBaseUrl(), provider.getTextModel(),
                provider.getEmbeddingModel(), provider.getVisionModel(), provider.capabilities().stream().toList(),
                Arrays.stream(AiCapability.values()).filter(provider.getAdapter()::supports).toList(),
                provider.getSecretName(), provider.getAdapter().requiresSecret(),
                secrets.has(provider.getSecretName()), provider.getInputPricePerMillion(),
                provider.getOutputPricePerMillion(), provider.isNoTrainingConfirmed(),
                provider.getNoTrainingConfirmedAt(), provider.isEnabled(), primaryFor, fallbackFor,
                provider.getUpdatedAt());
    }
}
