package com.learningcompanion.devtools;

import com.learningcompanion.ai.config.domain.AiFeature;
import com.learningcompanion.ai.quota.domain.QuotaTask;
import com.learningcompanion.ai.safety.api.SafetyStream;
import com.learningcompanion.ai.safety.application.SafeGenerationService;
import com.learningcompanion.ai.safety.application.SourcePassage;
import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.web.ApiPaths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Điều khiển bộ lọc an toàn (NCL-04-CN-004) để thử bằng tay hoặc trong kiểm thử, khi chưa có chức năng hỏi đáp của
 * người học: hỏi một câu (kèm đoạn nguồn) như người học qua đúng lối sinh nội dung chung, và đưa ngưỡng lọc cùng sổ
 * can thiệp về mặc định. Không bao giờ được bật ở môi trường thật.
 */
@RestController
@RequestMapping(ApiPaths.DEV + "/safety")
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class DevSafetyController {

    private final UserAccountRepository accounts;
    private final SafeGenerationService generation;
    private final JdbcTemplate jdbc;

    public DevSafetyController(UserAccountRepository accounts, SafeGenerationService generation, JdbcTemplate jdbc) {
        this.accounts = accounts;
        this.generation = generation;
        this.jdbc = jdbc;
    }

    @PostMapping("/ask")
    public Map<String, Object> ask(@RequestBody AskRequest request) {
        UserAccount account = accounts.findByEmail(request.email() == null ? ""
                        : request.email().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        List<SourcePassage> sources = request.sources() == null ? List.of() : request.sources().stream()
                .map(source -> SourcePassage.of(source.title(), source.text())).toList();
        SafeGenerationService.Outcome outcome = generation.generate(SafeGenerationService.Request
                .of(account.getId(), AiFeature.ASSISTANT_CHAT, request.question())
                .withSources(sources)
                .withQuota(request.task() == null ? QuotaTask.ASSISTANT_QUESTION : request.task()), chunk -> { });
        Map<String, Object> body = new LinkedHashMap<>(SafetyStream.safety(outcome));
        body.put("text", outcome.text());
        body.put("flaggedSources", outcome.flaggedSources());
        return body;
    }

    @PostMapping("/reset")
    @Transactional
    public ResponseEntity<Void> reset() {
        jdbc.update("delete from ai_safety_events");
        jdbc.update("update content_safety_levels set level = 'STANDARD', updated_by = null, updated_at = now()");
        return ResponseEntity.noContent().build();
    }

    public record Source(String title, String text) {
    }

    public record AskRequest(String email, String question, List<Source> sources, QuotaTask task) {
    }
}
