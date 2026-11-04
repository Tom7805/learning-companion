package com.learningcompanion.devtools;

import com.learningcompanion.ai.config.application.AiConfigService;
import com.learningcompanion.ai.gateway.CircuitBreakerRegistry;
import com.learningcompanion.ai.provider.mock.MockAiAdapter;
import com.learningcompanion.shared.web.ApiPaths;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Điều khiển bản mô phỏng của lớp kết nối trí tuệ nhân tạo (NCL-04-CN-001) để thử ngắt mạch, quá hạn và chuyển dự
 * phòng bằng tay hoặc trong kiểm thử đầu cuối. Không bao giờ được bật ở môi trường thật.
 */
@RestController
@RequestMapping(ApiPaths.DEV + "/ai")
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class DevAiGatewayController {

    private static final String PRIMARY = "6f1d0c1e-0000-4000-8000-000000000001";
    private static final String BACKUP = "6f1d0c1e-0000-4000-8000-000000000002";

    private final MockAiAdapter mock;
    private final CircuitBreakerRegistry breakers;
    private final JdbcTemplate jdbc;
    private final AiConfigService configs;

    public DevAiGatewayController(MockAiAdapter mock, CircuitBreakerRegistry breakers, JdbcTemplate jdbc,
                                  AiConfigService configs) {
        this.mock = mock;
        this.breakers = breakers;
        this.jdbc = jdbc;
        this.configs = configs;
    }

    /** Chế độ của một nhà cung cấp mô phỏng: OK, FAIL (lỗi ngay) hoặc HANG (không phản hồi). */
    @PostMapping("/mock/{providerCode}")
    public Map<String, String> mode(@PathVariable String providerCode, @RequestParam MockAiAdapter.Mode mode) {
        mock.setMode(providerCode, mode);
        return Map.of("providerCode", providerCode, "mode", mode.name());
    }

    /**
     * Câu trả lời cố định của bản mô phỏng cho các lời gọi sinh văn bản tiếp theo (ví dụ nội dung vi phạm để thử bộ
     * lọc đầu ra, NCL-04-CN-004). Gửi {@code text} rỗng để bỏ; đặt lại lớp kết nối cũng bỏ.
     */
    @PostMapping("/mock-reply")
    public ResponseEntity<Void> scriptedReply(@RequestBody Map<String, String> body) {
        String text = body.get("text");
        mock.setScriptedReply(text == null || text.isBlank() ? null : text);
        return ResponseEntity.noContent().build();
    }

    /**
     * Cho {@code calls} lời gọi nhận dạng chữ tiếp theo lỗi (âm: lỗi cho tới khi đặt lại), để thử lỗi nhận dạng ở một
     * trang (NCL-05-CN-003 TC-03). Đặt lại lớp kết nối cũng bỏ.
     */
    @PostMapping("/mock-ocr-failures")
    public ResponseEntity<Void> ocrFailures(@RequestBody Map<String, Integer> body) {
        Integer calls = body.get("calls");
        mock.failNextOcr(calls == null ? 0 : calls);
        return ResponseEntity.noContent().build();
    }

    /**
     * Đưa lớp kết nối về trạng thái ban đầu: hai bản mô phỏng bật, định tuyến mặc định, mạch đóng, cảnh báo đã xác
     * nhận, nhà cung cấp thêm trong lúc thử bị tắt.
     */
    @PostMapping("/reset")
    @Transactional
    public ResponseEntity<Void> reset(@RequestParam(defaultValue = "25") long chunkDelayMs) {
        mock.reset();
        mock.setChunkDelay(Duration.ofMillis(chunkDelayMs));
        breakers.reset();
        jdbc.update("update ai_providers set enabled = true, no_training_confirmed_at = coalesce("
                + "no_training_confirmed_at, now()) where id in (?::uuid, ?::uuid)", PRIMARY, BACKUP);
        jdbc.update("update ai_capability_routes set primary_provider_id = ?::uuid, fallback_provider_id = ?::uuid, "
                + "timeout_seconds = 30, max_retries = 1, breaker_failure_threshold = 5, breaker_window_seconds = 60, "
                + "breaker_open_seconds = 60, updated_at = now()", PRIMARY, BACKUP);
        jdbc.update("update ai_providers set enabled = false where id not in (?::uuid, ?::uuid)", PRIMARY, BACKUP);
        jdbc.update("update ai_gateway_alerts set acknowledged_at = now() where acknowledged_at is null");
        return ResponseEntity.noContent().build();
    }

    /**
     * Xóa mọi phiên bản cấu hình chức năng (NCL-04-CN-002) rồi đăng ký lại phiên bản 1 từ giá trị gốc, để kiểm thử
     * đầu cuối bắt đầu từ cùng một lịch sử.
     */
    @PostMapping("/configs/reset")
    @Transactional
    public ResponseEntity<Void> resetConfigs() {
        jdbc.update("delete from ai_config_versions");
        configs.registerFeatures();
        return ResponseEntity.noContent().build();
    }
}
