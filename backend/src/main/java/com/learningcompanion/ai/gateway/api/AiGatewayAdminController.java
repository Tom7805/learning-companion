package com.learningcompanion.ai.gateway.api;

import com.learningcompanion.ai.config.application.AiConfigResolver;
import com.learningcompanion.ai.config.domain.AiFeature;
import com.learningcompanion.ai.gateway.AiCapability;
import com.learningcompanion.ai.gateway.AiGateway;
import com.learningcompanion.ai.gateway.AiGatewayAdminService;
import com.learningcompanion.ai.gateway.api.dto.AiGatewayOverviewResponse;
import com.learningcompanion.ai.gateway.api.dto.AiProviderRequest;
import com.learningcompanion.ai.gateway.api.dto.AiProviderResponse;
import com.learningcompanion.ai.gateway.api.dto.AiRouteRequest;
import com.learningcompanion.ai.gateway.api.dto.AiRouteResponse;
import com.learningcompanion.ai.gateway.api.dto.AiTestCallRequest;
import com.learningcompanion.ai.gateway.api.dto.AiUsageResponse;
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
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Trang quản trị lớp kết nối dịch vụ trí tuệ nhân tạo (NCL-04-CN-001). Chỉ vai trò ADMIN gọi được; người khác bị từ
 * chối và lần từ chối được ghi nhật ký (TC-05, xem SecurityConfig).
 */
@RestController
@RequestMapping(ApiPaths.ADMIN + "/ai")
public class AiGatewayAdminController {

    /**
     * Mã chức năng của lời gọi thử, để tách khỏi mức sử dụng của người học. Lời nhắc, nhiệt độ và độ dài lấy từ phiên
     * bản cấu hình đang phát hành của chức năng này (NCL-04-CN-002).
     */
    public static final String TEST_FEATURE = AiFeature.ADMIN_TEST.name();

    private final AiGatewayAdminService admin;
    private final AiGateway gateway;
    private final AiConfigResolver configs;

    public AiGatewayAdminController(AiGatewayAdminService admin, AiGateway gateway, AiConfigResolver configs) {
        this.admin = admin;
        this.gateway = gateway;
        this.configs = configs;
    }

    @GetMapping("/gateway")
    public AiGatewayOverviewResponse overview() {
        return admin.overview();
    }

    @GetMapping("/usage")
    public AiUsageResponse usage(@RequestParam(defaultValue = "7") int days) {
        return admin.usage(days);
    }

    @PostMapping("/providers")
    @ResponseStatus(HttpStatus.CREATED)
    public AiProviderResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                     @Valid @RequestBody AiProviderRequest body, HttpServletRequest request) {
        return admin.create(user.userId(), body, ClientInfo.from(request));
    }

    @PutMapping("/providers/{id}")
    public AiProviderResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                     @Valid @RequestBody AiProviderRequest body, HttpServletRequest request) {
        return admin.update(user.userId(), id, body, ClientInfo.from(request));
    }

    @PostMapping("/providers/{id}/enable")
    public AiProviderResponse enable(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                     HttpServletRequest request) {
        return admin.enable(user.userId(), id, ClientInfo.from(request));
    }

    @PostMapping("/providers/{id}/disable")
    public AiProviderResponse disable(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                      HttpServletRequest request) {
        return admin.disable(user.userId(), id, ClientInfo.from(request));
    }

    @PutMapping("/routes/{capability}")
    public AiRouteResponse updateRoute(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable AiCapability capability, @Valid @RequestBody AiRouteRequest body,
                                       HttpServletRequest request) {
        return admin.updateRoute(user.userId(), capability, body, ClientInfo.from(request));
    }

    @PostMapping("/routes/{capability}/switch")
    public AiRouteResponse switchToFallback(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable AiCapability capability, HttpServletRequest request) {
        return admin.switchToFallback(user.userId(), capability, ClientInfo.from(request));
    }

    @PostMapping("/alerts/{id}/acknowledge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acknowledge(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                            HttpServletRequest request) {
        admin.acknowledgeAlert(user.userId(), id, ClientInfo.from(request));
    }

    /**
     * Lời gọi thử sinh văn bản theo luồng: mỗi đoạn là một sự kiện "chunk", kết thúc bằng "done" (nhà cung cấp, số đơn
     * vị) hoặc "error" (mã lỗi). Đi qua lớp kết nối nên được đo như mọi lời gọi khác.
     */
    @PostMapping(path = "/test-call", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter testCall(@Valid @RequestBody AiTestCallRequest body) {
        ChatRequest request = configs.chatRequest(AiFeature.ADMIN_TEST, body.prompt());
        SseEmitter emitter = new SseEmitter(0L);
        Thread.ofVirtual().name("ai-test-call").start(() -> {
            try {
                ChatResult result = gateway.streamText(TEST_FEATURE, request,
                        chunk -> send(emitter, "chunk", Map.of("text", chunk.text())));
                Map<String, Object> done = new LinkedHashMap<>();
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
