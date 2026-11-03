package com.learningcompanion.devtools;

import com.learningcompanion.ai.gateway.AiGateway;
import com.learningcompanion.ai.gateway.model.ChatRequest;
import com.learningcompanion.ai.gateway.model.ChatResult;
import com.learningcompanion.ai.quota.application.LearnerDay;
import com.learningcompanion.ai.quota.application.QuotaReservationService;
import com.learningcompanion.ai.quota.application.QuotaService;
import com.learningcompanion.ai.quota.domain.QuotaReservation;
import com.learningcompanion.ai.quota.domain.QuotaTask;
import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.web.ApiPaths;
import java.util.LinkedHashMap;
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
 * Điều khiển sổ lượt (NCL-04-CN-003) để thử bằng tay hoặc trong kiểm thử đầu cuối, khi chưa có chức năng học tập nào
 * tốn lượt: chạy một tác vụ tốn lượt thật qua lớp kết nối (bản mô phỏng) cho một tài khoản, đặt số lượt đã dùng hôm nay
 * và đưa hạn mức về mặc định. Không bao giờ được bật ở môi trường thật.
 */
@RestController
@RequestMapping(ApiPaths.DEV + "/quota")
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class DevQuotaController {

    private final UserAccountRepository accounts;
    private final QuotaReservationService reservations;
    private final QuotaService quotas;
    private final LearnerDay learnerDay;
    private final AiGateway gateway;
    private final JdbcTemplate jdbc;

    public DevQuotaController(UserAccountRepository accounts, QuotaReservationService reservations,
                              QuotaService quotas, LearnerDay learnerDay, AiGateway gateway, JdbcTemplate jdbc) {
        this.accounts = accounts;
        this.reservations = reservations;
        this.quotas = quotas;
        this.learnerDay = learnerDay;
        this.gateway = gateway;
        this.jdbc = jdbc;
    }

    /**
     * Chạy một tác vụ tốn lượt cho tài khoản. {@code cancel} thì giữ chỗ rồi hủy ngay. Lỗi của lớp kết nối (ví dụ khi
     * bản mô phỏng được đặt FAIL) trả về như chức năng thật: lượt được hoàn lại và lỗi kèm {@code quotaRefunded}.
     */
    @PostMapping("/tasks")
    public Map<String, Object> run(@RequestBody TaskRequest request) {
        UserAccount account = account(request.email());
        if (Boolean.TRUE.equals(request.cancel())) {
            QuotaReservationService.Reservation reservation = reservations.reserve(account.getId(), request.task());
            reservations.release(reservation.id(), QuotaReservation.ReleaseReason.CANCELLED);
            return Map.of("task", request.task().name(), "units", reservation.units(), "outcome", "CANCELLED");
        }
        ChatResult result = reservations.metered(account.getId(), request.task(),
                () -> gateway.streamText(request.task().name(),
                        ChatRequest.of("Bạn là trợ lý học tập.", "Tóm tắt định luật Ohm trong một câu."),
                        chunk -> { }));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("task", request.task().name());
        body.put("outcome", "COMMITTED");
        body.put("providerCode", result.providerCode());
        return body;
    }

    /** Đặt số lượt đã dùng trong ngày học hiện tại của tài khoản, để thử trạng thái hết lượt. */
    @PostMapping("/usage")
    @Transactional
    public ResponseEntity<Void> setUsage(@RequestBody UsageRequest request) {
        UserAccount account = account(request.email());
        LearnerDay.Today today = learnerDay.today(account.getId());
        jdbc.update("""
                insert into quota_daily_usage (owner_id, study_day, used_units, reserved_units) values (?, ?, ?, 0)
                on conflict (owner_id, study_day) do update set used_units = excluded.used_units""",
                account.getId(), today.day(), Math.max(0, request.usedUnits()));
        return ResponseEntity.noContent().build();
    }

    /** Xóa sổ lượt và đưa hạn mức chung cùng bảng quy đổi về mặc định lúc cài đặt. */
    @PostMapping("/reset")
    @Transactional
    public ResponseEntity<Void> reset() {
        jdbc.update("delete from quota_reservations");
        jdbc.update("delete from quota_daily_usage");
        jdbc.update("update quota_policies set daily_ai_units = 50, storage_limit_bytes = 1073741824, "
                + "updated_by = null, updated_at = now()");
        jdbc.update("delete from quota_task_costs");
        quotas.registerTasks();
        return ResponseEntity.noContent().build();
    }

    private UserAccount account(String email) {
        return accounts.findByEmail(email == null ? "" : email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public record TaskRequest(String email, QuotaTask task, Boolean cancel) {
    }

    public record UsageRequest(String email, int usedUnits) {
    }
}
