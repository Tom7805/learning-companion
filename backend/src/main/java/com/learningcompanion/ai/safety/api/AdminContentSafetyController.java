package com.learningcompanion.ai.safety.api;

import com.learningcompanion.ai.config.domain.AiFeature;
import com.learningcompanion.ai.safety.api.dto.ContentSafetyOverviewResponse;
import com.learningcompanion.ai.safety.api.dto.SafetyLevelsRequest;
import com.learningcompanion.ai.safety.api.dto.SafetyTrialRequest;
import com.learningcompanion.ai.safety.application.ContentSafetyService;
import com.learningcompanion.ai.safety.application.SafeGenerationService;
import com.learningcompanion.ai.safety.application.SourcePassage;
import com.learningcompanion.ai.gateway.model.ChatChunk;
import com.learningcompanion.shared.security.AuthenticatedUser;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Ngưỡng lọc nội dung và chạy thử bộ lọc (NCL-04-CN-004). Nằm trong khu quản trị nên chỉ vai trò ADMIN gọi được;
 * người học gửi yêu cầu đổi ngưỡng thì bị từ chối và lần từ chối được ghi nhật ký (TC-05, xem SecurityConfig).
 */
@RestController
@RequestMapping(ApiPaths.ADMIN + "/content-safety")
public class AdminContentSafetyController {

    /** Mã đo mức sử dụng của lần chạy thử, tách khỏi lời gọi của người học. */
    static final String TRIAL_FEATURE = "SAFETY_TRIAL";

    private final ContentSafetyService safety;
    private final SafeGenerationService generation;

    public AdminContentSafetyController(ContentSafetyService safety, SafeGenerationService generation) {
        this.safety = safety;
        this.generation = generation;
    }

    @GetMapping
    public ContentSafetyOverviewResponse overview() {
        return safety.overview();
    }

    @PutMapping("/levels")
    public ContentSafetyOverviewResponse updateLevels(@AuthenticationPrincipal AuthenticatedUser user,
                                                      @Valid @RequestBody SafetyLevelsRequest body,
                                                      HttpServletRequest request) {
        return safety.updateLevels(user.userId(), body, ClientInfo.from(request));
    }

    /**
     * Chạy thử một câu hỏi (kèm một đoạn nguồn mẫu nếu có) qua đúng lối đi của trợ lý hỏi đáp: bộ lọc đầu vào, lớp
     * bao dữ liệu, cấu hình đang phát hành, bộ lọc đầu ra. Can thiệp trong lúc thử cũng được ghi sự kiện như thật.
     */
    @PostMapping(path = "/trial", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter trial(@AuthenticationPrincipal AuthenticatedUser user,
                            @Valid @RequestBody SafetyTrialRequest body, HttpServletRequest http) {
        List<SourcePassage> sources = body.source() == null || body.source().isBlank() ? List.of()
                : List.of(SourcePassage.of("Đoạn nguồn mẫu", body.source().strip()));
        SafeGenerationService.Request request = SafeGenerationService.Request
                .of(user.userId(), AiFeature.ASSISTANT_CHAT, body.question().strip())
                .withMeterCode(TRIAL_FEATURE)
                .withSources(sources)
                .withClient(ClientInfo.from(http));
        return SafetyStream.start("ai-safety-trial",
                chunks -> generation.generate(request, (ChatChunk chunk) -> chunks.accept(chunk.text())),
                (outcome, done) -> { });
    }
}
