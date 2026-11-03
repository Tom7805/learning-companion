package com.learningcompanion.ai.config.api;

import com.learningcompanion.ai.config.api.dto.AiConfigDiffResponse;
import com.learningcompanion.ai.config.api.dto.AiConfigOverviewResponse;
import com.learningcompanion.ai.config.api.dto.AiConfigVersionRequest;
import com.learningcompanion.ai.config.api.dto.AiConfigVersionResponse;
import com.learningcompanion.ai.config.api.dto.PromptTrialRequest;
import com.learningcompanion.ai.config.application.AiConfigResolver;
import com.learningcompanion.ai.config.application.AiConfigService;
import com.learningcompanion.ai.config.application.PromptTrialSamples;
import com.learningcompanion.ai.config.domain.AiFeature;
import com.learningcompanion.ai.gateway.AiGateway;
import com.learningcompanion.ai.gateway.model.ChatRequest;
import com.learningcompanion.ai.gateway.model.ChatResult;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.security.AuthenticatedUser;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Cấu hình mô hình và lời nhắc hệ thống theo phiên bản (NCL-04-CN-002). Nằm trong khu quản trị nên chỉ vai trò ADMIN
 * gọi được; người khác bị từ chối và lần từ chối được ghi nhật ký (TC-04, xem SecurityConfig).
 */
@RestController
@RequestMapping(ApiPaths.ADMIN + "/ai/configs")
public class AiConfigController {

    /** Mã chức năng của lời gọi thử cấu hình, để tách khỏi mức sử dụng của người học. */
    public static final String TRIAL_FEATURE = "ADMIN_CONFIG_TRIAL";

    private final AiConfigService configs;
    private final AiGateway gateway;

    public AiConfigController(AiConfigService configs, AiGateway gateway) {
        this.configs = configs;
        this.gateway = gateway;
    }

    @GetMapping
    public AiConfigOverviewResponse overview() {
        return configs.overview();
    }

    @GetMapping("/{feature}/versions")
    public List<AiConfigVersionResponse> history(@PathVariable AiFeature feature) {
        return configs.history(feature);
    }

    @GetMapping("/{feature}/versions/{number}")
    public AiConfigVersionResponse version(@PathVariable AiFeature feature, @PathVariable int number) {
        return configs.version(feature, number);
    }

    @GetMapping("/{feature}/diff")
    public AiConfigDiffResponse diff(@PathVariable AiFeature feature, @RequestParam int from, @RequestParam int to) {
        return configs.diff(feature, from, to);
    }

    @PostMapping("/{feature}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public AiConfigVersionResponse save(@AuthenticationPrincipal AuthenticatedUser user,
                                        @PathVariable AiFeature feature,
                                        @Valid @RequestBody AiConfigVersionRequest body, HttpServletRequest request) {
        return configs.saveDraft(user.userId(), feature, body, ClientInfo.from(request));
    }

    @PostMapping("/{feature}/versions/{number}/publish")
    public AiConfigVersionResponse publish(@AuthenticationPrincipal AuthenticatedUser user,
                                           @PathVariable AiFeature feature, @PathVariable int number,
                                           HttpServletRequest request) {
        return configs.publish(user.userId(), feature, number, ClientInfo.from(request));
    }

    @PostMapping("/{feature}/rollback")
    public AiConfigVersionResponse rollback(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable AiFeature feature, HttpServletRequest request) {
        return configs.rollback(user.userId(), feature, ClientInfo.from(request));
    }

    /**
     * Thử nhanh một phiên bản: câu hỏi mẫu cùng bộ nguồn mẫu đi qua lớp kết nối với đúng nhà cung cấp, mô hình, nhiệt
     * độ và lời nhắc của phiên bản đó. Trả theo luồng sự kiện như lời gọi thử của lớp kết nối: "chunk", rồi "done" hoặc
     * "error". Thử xong thì phiên bản nháp mới phát hành được.
     */
    @PostMapping(path = "/{feature}/versions/{number}/trial", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter trial(@PathVariable AiFeature feature, @PathVariable int number,
                            @Valid @RequestBody PromptTrialRequest body) {
        PromptTrialSamples.SampleSet samples = PromptTrialSamples.find(body.sampleSetId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "sampleSetId"));
        AiConfigVersionResponse version = configs.version(feature, number);
        ChatRequest request = new AiConfigResolver.ResolvedConfig(feature, number, version.providerCode(),
                version.model(), version.temperature(), version.maxOutputTokens(), version.systemPrompt())
                .toRequest(List.of(new ChatRequest.Message(ChatRequest.Role.USER,
                        PromptTrialSamples.userMessage(samples, body.question().strip()))));
        SseEmitter emitter = new SseEmitter(0L);
        Thread.ofVirtual().name("ai-config-trial").start(() -> {
            try {
                ChatResult result = gateway.streamText(TRIAL_FEATURE, request,
                        chunk -> send(emitter, "chunk", Map.of("text", chunk.text())));
                configs.recordTrial(feature, number);
                Map<String, Object> done = new LinkedHashMap<>();
                done.put("version", number);
                done.put("providerCode", result.providerCode());
                done.put("inputUnits", result.usage().inputUnits());
                done.put("outputUnits", result.usage().outputUnits());
                send(emitter, "done", done);
                emitter.complete();
            } catch (BusinessException ex) {
                send(emitter, "error", Map.of("code", ex.code().name(), "message", ex.getMessage()));
                emitter.complete();
            } catch (RuntimeException ex) {
                send(emitter, "error", Map.of("code", ErrorCode.INTERNAL_ERROR.name(),
                        "message", ErrorCode.INTERNAL_ERROR.defaultMessage()));
                emitter.complete();
            }
        });
        return emitter;
    }

    private static void send(SseEmitter emitter, String event, Map<String, Object> data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            // Trình duyệt đã đóng kết nối; lời gọi vẫn chạy hết và vẫn được đo.
        }
    }
}
