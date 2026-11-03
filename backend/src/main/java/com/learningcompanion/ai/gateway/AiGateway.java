package com.learningcompanion.ai.gateway;

import com.learningcompanion.ai.gateway.model.AiUsage;
import com.learningcompanion.ai.gateway.model.ChatChunk;
import com.learningcompanion.ai.gateway.model.ChatRequest;
import com.learningcompanion.ai.gateway.model.ChatResult;
import com.learningcompanion.ai.gateway.model.EmbeddingRequest;
import com.learningcompanion.ai.gateway.model.EmbeddingResult;
import com.learningcompanion.ai.gateway.model.OcrResult;
import com.learningcompanion.ai.gateway.model.VisionRequest;
import com.learningcompanion.ai.provider.AdapterType;
import com.learningcompanion.ai.provider.AiProviderAdapter;
import com.learningcompanion.ai.provider.ProviderCallException;
import com.learningcompanion.ai.provider.ProviderDescriptor;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Lớp kết nối dịch vụ trí tuệ nhân tạo (NCL-04-CN-001, QTN-04). Mọi chức năng gọi mô hình chỉ đi qua đây, không bao
 * giờ gọi thẳng nhà cung cấp.
 *
 * <p>Với mỗi lời gọi: đọc định tuyến của khả năng, chọn nhà cung cấp chính (hoặc dự phòng nếu mạch của nhà cung cấp
 * chính đang mở), gọi với thời gian chờ, thử lại đúng số lần cấu hình, và ghi một dòng đo mức sử dụng (đơn vị, độ trễ,
 * chi phí, nhà cung cấp) không kèm nội dung. Lỗi liên tiếp đủ ngưỡng thì ngắt mạch, cảnh báo quản trị viên và chuyển
 * lời gọi sang dự phòng. Hết số lần thử thì chức năng gọi nhận lỗi tạm thời {@link ErrorCode#AI_TEMPORARILY_UNAVAILABLE}.
 */
@Service
public class AiGateway {

    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);
    private static final Pattern FEATURE = Pattern.compile("[A-Z][A-Z0-9_]{1,59}");

    private final AiCapabilityRouteRepository routes;
    private final AiProviderConfigRepository providers;
    private final Map<AdapterType, AiProviderAdapter> adapters = new EnumMap<>(AdapterType.class);
    private final CircuitBreakerRegistry breakers;
    private final UsageMeter meter;
    private final AiGatewayAlertService alerts;
    private final AiSecretStore secrets;
    private final ExecutorService calls = Executors.newVirtualThreadPerTaskExecutor();

    public AiGateway(AiCapabilityRouteRepository routes, AiProviderConfigRepository providers,
                     List<AiProviderAdapter> adapters, CircuitBreakerRegistry breakers, UsageMeter meter,
                     AiGatewayAlertService alerts, AiSecretStore secrets) {
        this.routes = routes;
        this.providers = providers;
        for (AiProviderAdapter adapter : adapters) {
            if (this.adapters.put(adapter.type(), adapter) != null) {
                throw new IllegalStateException("Hai bộ chuyển đổi cho cùng kiểu " + adapter.type());
            }
        }
        this.breakers = breakers;
        this.meter = meter;
        this.alerts = alerts;
        this.secrets = secrets;
    }

    /**
     * Sinh văn bản theo luồng: mỗi đoạn được đưa ngay cho {@code onChunk}. Khi đã có đoạn gửi đi thì không thử lại
     * nữa, vì người dùng đã thấy một phần câu trả lời.
     *
     * @param feature mã chức năng gọi, chữ in hoa, ví dụ ASSISTANT_CHAT; dùng để chia mức sử dụng theo chức năng
     */
    public ChatResult streamText(String feature, ChatRequest request, Consumer<ChatChunk> onChunk) {
        Objects.requireNonNull(onChunk, "onChunk");
        return execute(AiCapability.TEXT_GENERATION, feature, ChatResult::usage, ChatResult::withProvider,
                (adapter, provider, attempt) -> adapter.streamText(provider, request, chunk -> {
                    if (attempt.deliver()) {
                        onChunk.accept(chunk);
                    }
                }), true);
    }

    public EmbeddingResult embed(String feature, EmbeddingRequest request) {
        return execute(AiCapability.EMBEDDING, feature, EmbeddingResult::usage, EmbeddingResult::withProvider,
                (adapter, provider, attempt) -> adapter.embed(provider, request), false);
    }

    public ChatResult describeImage(String feature, VisionRequest request) {
        return execute(AiCapability.VISION, feature, ChatResult::usage, ChatResult::withProvider,
                (adapter, provider, attempt) -> adapter.describeImage(provider, request), false);
    }

    public OcrResult recognizeText(String feature, VisionRequest request) {
        return execute(AiCapability.OCR, feature, OcrResult::usage, OcrResult::withProvider,
                (adapter, provider, attempt) -> adapter.recognizeText(provider, request), false);
    }

    @PreDestroy
    void shutdown() {
        calls.shutdownNow();
    }

    @FunctionalInterface
    private interface ProviderCall<T> {
        T call(AiProviderAdapter adapter, ProviderDescriptor provider, Attempt attempt);
    }

    private <T> T execute(AiCapability capability, String feature, Function<T, AiUsage> usageOf,
                          ProviderCallResultNamer<T> namer, ProviderCall<T> call, boolean streaming) {
        if (feature == null || !FEATURE.matcher(feature).matches()) {
            throw new IllegalArgumentException("Mã chức năng không hợp lệ: " + feature);
        }
        long started = System.nanoTime();
        AiCapabilityRoute route = routes.findById(capability).orElse(null);
        AiProviderConfig primary = route == null ? null : load(route.getPrimaryProviderId());
        AiProviderConfig fallback = route == null ? null : load(route.getFallbackProviderId());
        Duration openFor = route == null ? Duration.ZERO : Duration.ofSeconds(route.getBreakerOpenSeconds());

        AiProviderConfig current;
        boolean usedFallback = false;
        if (usable(primary, capability) && breakers.allows(capability, primary.getCode(), openFor)) {
            current = primary;
        } else if (usable(fallback, capability) && breakers.allows(capability, fallback.getCode(), openFor)) {
            current = fallback;
            usedFallback = true;
        } else {
            meter.record(capability, feature, null, AiCallRecord.Outcome.UNAVAILABLE, false, 0, AiUsage.NONE,
                    elapsedMs(started), "NO_PROVIDER");
            throw unavailable(capability);
        }

        int attempts = 0;
        int attemptsOnCurrent = 0;
        String lastError = null;
        boolean anyChunk = false;
        while (true) {
            attempts++;
            attemptsOnCurrent++;
            Attempt attempt = new Attempt();
            AiProviderConfig provider = current;
            Future<T> future = calls.submit(() -> call.call(adapterFor(provider), describe(provider, capability),
                    attempt));
            try {
                T result = future.get(route.getTimeoutSeconds(), TimeUnit.SECONDS);
                breakers.recordSuccess(capability, provider.getCode());
                meter.record(capability, feature, provider, AiCallRecord.Outcome.SUCCESS, usedFallback, attempts,
                        usageOf.apply(result), elapsedMs(started), null);
                return namer.withProvider(result, provider.getCode());
            } catch (TimeoutException ex) {
                attempt.close();
                future.cancel(true);
                lastError = "TIMEOUT";
            } catch (ExecutionException ex) {
                attempt.close();
                lastError = ex.getCause() instanceof ProviderCallException failure ? failure.errorCode()
                        : "UNEXPECTED";
                if (!(ex.getCause() instanceof ProviderCallException)) {
                    log.warn("Lỗi không lường trước khi gọi {} ({})", provider.getCode(), capability, ex.getCause());
                }
            } catch (InterruptedException ex) {
                attempt.close();
                future.cancel(true);
                Thread.currentThread().interrupt();
                lastError = "INTERRUPTED";
                break;
            } catch (CancellationException ex) {
                lastError = "CANCELLED";
            }
            anyChunk |= attempt.emitted();
            log.info("Lời gọi {} tới {} thất bại lần {}: {}", capability, provider.getCode(), attemptsOnCurrent,
                    lastError);
            boolean opened = breakers.recordFailure(capability, provider.getCode(),
                    route.getBreakerFailureThreshold(), Duration.ofSeconds(route.getBreakerWindowSeconds()));
            boolean canFallBack = !usedFallback && usable(fallback, capability);
            if (opened) {
                alerts.circuitOpened(capability, provider.getCode(), canFallBack ? fallback.getCode() : null);
            }
            if (streaming && anyChunk) {
                break;
            }
            if (opened && canFallBack) {
                current = fallback;
                usedFallback = true;
                attemptsOnCurrent = 0;
                continue;
            }
            if (attemptsOnCurrent > route.getMaxRetries()) {
                break;
            }
        }
        meter.record(capability, feature, current,
                "TIMEOUT".equals(lastError) ? AiCallRecord.Outcome.TIMEOUT : AiCallRecord.Outcome.FAILURE,
                usedFallback, attempts, AiUsage.NONE, elapsedMs(started), lastError);
        throw unavailable(capability);
    }

    @FunctionalInterface
    private interface ProviderCallResultNamer<T> {
        T withProvider(T result, String providerCode);
    }

    /**
     * Một lần gọi tới nhà cung cấp. Hết thời gian chờ thì lần gọi bị đóng: đoạn văn bản đến muộn bị bỏ, không lẫn vào
     * lần thử sau.
     */
    private static final class Attempt {

        private final AtomicBoolean live = new AtomicBoolean(true);
        private final AtomicBoolean emitted = new AtomicBoolean(false);

        boolean deliver() {
            if (live.get()) {
                emitted.set(true);
                return true;
            }
            return false;
        }

        void close() {
            live.set(false);
        }

        boolean emitted() {
            return emitted.get();
        }
    }

    private AiProviderConfig load(java.util.UUID id) {
        return id == null ? null : providers.findById(id).orElse(null);
    }

    private static boolean usable(AiProviderConfig provider, AiCapability capability) {
        return provider != null && provider.isEnabled() && provider.isNoTrainingConfirmed()
                && provider.supports(capability);
    }

    private AiProviderAdapter adapterFor(AiProviderConfig provider) {
        AiProviderAdapter adapter = adapters.get(provider.getAdapter());
        if (adapter == null) {
            throw new ProviderCallException("NO_ADAPTER", "Chưa có bộ chuyển đổi cho " + provider.getAdapter());
        }
        return adapter;
    }

    private ProviderDescriptor describe(AiProviderConfig provider, AiCapability capability) {
        String secret = null;
        if (provider.getAdapter().requiresSecret()) {
            secret = secrets.resolve(provider.getSecretName());
            if (secret == null) {
                throw new ProviderCallException("SECRET_MISSING", "Kho bí mật chưa có khóa của " + provider.getCode());
            }
        }
        return new ProviderDescriptor(provider.getCode(), provider.getAdapter(), provider.getBaseUrl(),
                provider.getTextModel(), provider.getEmbeddingModel(), provider.getVisionModel(), secret);
    }

    private static BusinessException unavailable(AiCapability capability) {
        return new BusinessException(ErrorCode.AI_TEMPORARILY_UNAVAILABLE, null,
                Map.of("capability", capability.name(), "retryable", true));
    }

    private static long elapsedMs(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }
}
