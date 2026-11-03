package com.learningcompanion.ai.gateway.api;

import com.learningcompanion.ai.config.domain.AiFeature;
import com.learningcompanion.ai.gateway.AiCapability;
import com.learningcompanion.ai.gateway.AiGatewayAdminService;
import com.learningcompanion.ai.gateway.api.dto.AiGatewayOverviewResponse;
import com.learningcompanion.ai.gateway.api.dto.AiProviderRequest;
import com.learningcompanion.ai.gateway.api.dto.AiProviderResponse;
import com.learningcompanion.ai.gateway.api.dto.AiRouteRequest;
import com.learningcompanion.ai.gateway.api.dto.AiRouteResponse;
import com.learningcompanion.ai.gateway.api.dto.AiTestCallRequest;
import com.learningcompanion.ai.gateway.api.dto.AiUsageResponse;
import com.learningcompanion.ai.safety.api.SafetyStream;
import com.learningcompanion.ai.safety.application.SafeGenerationService;
import com.learningcompanion.shared.security.AuthenticatedUser;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
    private final SafeGenerationService generation;

    public AiGatewayAdminController(AiGatewayAdminService admin, SafeGenerationService generation) {
        this.admin = admin;
        this.generation = generation;
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
     * vị) hoặc "error" (mã lỗi). Đi qua lớp kết nối nên được đo như mọi lời gọi khác, và qua bộ lọc an toàn như mọi
     * lời gọi sinh nội dung (NCL-04-CN-004): bị chặn thì có thêm sự kiện "safety".
     */
    @PostMapping(path = "/test-call", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter testCall(@AuthenticationPrincipal AuthenticatedUser user,
                               @Valid @RequestBody AiTestCallRequest body, HttpServletRequest http) {
        SafeGenerationService.Request request = SafeGenerationService.Request
                .of(user.userId(), AiFeature.ADMIN_TEST, body.prompt())
                .withMeterCode(TEST_FEATURE)
                .withClient(ClientInfo.from(http));
        return SafetyStream.start("ai-test-call",
                chunks -> generation.generate(request, chunk -> chunks.accept(chunk.text())),
                (outcome, done) -> { });
    }
}
