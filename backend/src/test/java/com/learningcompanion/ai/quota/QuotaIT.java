package com.learningcompanion.ai.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.learningcompanion.admin.application.TotpService;
import com.learningcompanion.ai.quota.application.QuotaReservationService;
import com.learningcompanion.ai.quota.application.StaleReservationJob;
import com.learningcompanion.ai.quota.domain.QuotaTask;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.mail.MockMailGateway;
import com.learningcompanion.shared.time.AdjustableClock;
import com.learningcompanion.support.PostgresTestConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** NCL-04-CN-003 — hạn mức công bằng cho trí tuệ nhân tạo và dung lượng lưu trữ, kiểm qua API và cơ sở dữ liệu thật. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class QuotaIT {

    private static final String PASSWORD = "han-muc-cong-bang-2026";
    private static final String CHROME_ON_WINDOWS = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TotpService totp;

    @Autowired
    private AdjustableClock clock;

    @Autowired
    private MockMailGateway mailbox;

    @Autowired
    private QuotaReservationService reservations;

    @Autowired
    private StaleReservationJob staleJob;

    @BeforeEach
    void fresh() throws Exception {
        clock.reset();
        mvc.perform(post("/api/v1/dev/ai/reset?chunkDelayMs=0")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/dev/quota/reset")).andExpect(status().isNoContent());
    }

    @AfterEach
    void restore() throws Exception {
        clock.reset();
        mvc.perform(post("/api/v1/dev/ai/reset")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/dev/quota/reset")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Đồng hồ lượt mặc định: 50 lượt mỗi ngày, 1 GiB lưu trữ, bảng quy đổi tác vụ nhẹ 1 lượt và dài nhiều hơn")
    void defaultStatus() throws Exception {
        User learner = learner();
        mvc.perform(get("/api/v1/quota").cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ai.dailyUnits").value(50))
                .andExpect(jsonPath("$.ai.usedUnits").value(0))
                .andExpect(jsonPath("$.ai.remainingUnits").value(50))
                .andExpect(jsonPath("$.ai.timeZone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.storage.limitBytes").value(1073741824))
                .andExpect(jsonPath("$.costs[0].task").value("ASSISTANT_QUESTION"))
                .andExpect(jsonPath("$.costs[0].weight").value("LIGHT"))
                .andExpect(jsonPath("$.costs[0].units").value(1))
                .andExpect(jsonPath("$.costs[?(@.task == 'QUIZ_GENERATION')].units").value(5));
        mvc.perform(get("/api/v1/quota")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-01: còn mười lượt, tác vụ tốn một lượt chạy xong thì sổ trừ đúng một lượt, đồng hồ còn chín")
    void commitsOneUnit() throws Exception {
        User learner = learner();
        setUsage(learner, 40);
        mvc.perform(get("/api/v1/quota").cookie(learner.cookies()))
                .andExpect(jsonPath("$.ai.remainingUnits").value(10));

        runTask(learner, "ASSISTANT_QUESTION").andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("COMMITTED"));

        mvc.perform(get("/api/v1/quota").cookie(learner.cookies()))
                .andExpect(jsonPath("$.ai.usedUnits").value(41))
                .andExpect(jsonPath("$.ai.reservedUnits").value(0))
                .andExpect(jsonPath("$.ai.remainingUnits").value(9));
        assertThat(jdbc.queryForList("select status, units from quota_reservations where owner_id = ?::uuid",
                learner.id())).containsExactly(Map.of("status", "COMMITTED", "units", 1));
        // Lời gọi lớp kết nối được đo theo mã của loại tác vụ.
        assertThat(jdbc.queryForObject("select count(*) from ai_call_records where feature = 'ASSISTANT_QUESTION' "
                + "and outcome = 'SUCCESS'", Long.class)).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("TC-02: tác vụ đã giữ năm lượt lỗi do dịch vụ bên ngoài thì năm lượt được hoàn lại và người học được "
            + "báo không bị trừ lượt")
    void refundsOnProviderFailure() throws Exception {
        User learner = learner();
        mvc.perform(post("/api/v1/dev/ai/mock/mock-primary?mode=FAIL")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/dev/ai/mock/mock-backup?mode=FAIL")).andExpect(status().isOk());

        runTask(learner, "QUIZ_GENERATION").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_TEMPORARILY_UNAVAILABLE"))
                .andExpect(jsonPath("$.details.quotaRefunded").value(true))
                .andExpect(jsonPath("$.details.refundedUnits").value(5));

        mvc.perform(get("/api/v1/quota").cookie(learner.cookies()))
                .andExpect(jsonPath("$.ai.usedUnits").value(0))
                .andExpect(jsonPath("$.ai.reservedUnits").value(0))
                .andExpect(jsonPath("$.ai.remainingUnits").value(50));
        assertThat(jdbc.queryForMap("select status, release_reason, units from quota_reservations "
                + "where owner_id = ?::uuid", learner.id()))
                .containsEntry("status", "RELEASED").containsEntry("release_reason", "FAILED")
                .containsEntry("units", 5);
        mvc.perform(get("/api/v1/notifications").cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("AI_TASK_REFUNDED"))
                .andExpect(jsonPath("$.items[0].params.units").value(5))
                .andExpect(jsonPath("$.items[0].params.task").value("QUIZ_GENERATION"))
                .andExpect(jsonPath("$.items[0].targetType").value("USAGE"));
    }

    @Test
    @DisplayName("TC-02: tác vụ bị hủy thì lượt được hoàn lại, không có thông báo lỗi; chốt hai lần không trừ thêm")
    void cancelReleasesAndSettlesOnce() throws Exception {
        User learner = learner();
        mvc.perform(post("/api/v1/dev/quota/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"task\":\"STUDY_GUIDE\",\"cancel\":true}"
                                .formatted(learner.email())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("CANCELLED"));
        assertThat(jdbc.queryForObject("select release_reason from quota_reservations where owner_id = ?::uuid",
                String.class, learner.id())).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select count(*) from notifications where owner_id = ?::uuid "
                + "and type = 'AI_TASK_REFUNDED'", Long.class, learner.id())).isZero();

        UUID owner = UUID.fromString(learner.id());
        QuotaReservationService.Reservation reservation = reservations.reserve(owner, QuotaTask.DOCUMENT_SUMMARY);
        assertThat(reservations.commit(reservation.id())).isTrue();
        assertThat(reservations.commit(reservation.id())).isFalse();
        assertThat(reservations.release(reservation.id(),
                com.learningcompanion.ai.quota.domain.QuotaReservation.ReleaseReason.FAILED)).isFalse();
        mvc.perform(get("/api/v1/quota").cookie(learner.cookies()))
                .andExpect(jsonPath("$.ai.usedUnits").value(3))
                .andExpect(jsonPath("$.ai.reservedUnits").value(0));
    }

    @Test
    @DisplayName("TC-03: hết lượt thì mọi thao tác đọc vẫn chạy, chỉ tác vụ mới tốn lượt bị chặn kèm thời điểm hồi lại")
    void exhaustedBlocksOnlyNewTasks() throws Exception {
        User learner = learner();
        mvc.perform(post("/api/v1/dev/library/sample").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(learner.email()))).andExpect(status().isOk());
        setUsage(learner, 50);

        String body = runTask(learner, "ASSISTANT_QUESTION").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AI_QUOTA_EXHAUSTED"))
                .andExpect(jsonPath("$.details.remainingUnits").value(0))
                .andExpect(jsonPath("$.details.requiredUnits").value(1))
                .andExpect(jsonPath("$.details.timeZone").value("Asia/Ho_Chi_Minh"))
                .andReturn().getResponse().getContentAsString();
        Instant resetsAt = Instant.parse(JsonPath.read(body, "$.details.resetsAt"));
        ZonedDateTime local = resetsAt.atZone(ZoneId.of("Asia/Ho_Chi_Minh"));
        assertThat(local.toLocalTime().toSecondOfDay()).isZero();
        assertThat(local.toLocalDate()).isEqualTo(LocalDate.ofInstant(clock.instant(), local.getZone()).plusDays(1));
        assertThat(jdbc.queryForObject("select count(*) from quota_reservations where owner_id = ?::uuid",
                Long.class, learner.id())).isZero();

        // Thao tác đọc không tốn lượt vẫn chạy bình thường.
        mvc.perform(get("/api/v1/library/documents").cookie(learner.cookies())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/quota").cookie(learner.cookies())).andExpect(status().isOk())
                .andExpect(jsonPath("$.ai.remainingUnits").value(0))
                .andExpect(jsonPath("$.ai.resetsAt").value(resetsAt.toString()));
        mvc.perform(get("/api/v1/notifications").cookie(learner.cookies())).andExpect(status().isOk());

        // Còn hai lượt mà tác vụ cần năm thì cũng bị chặn, tác vụ nhẹ vẫn chạy.
        setUsage(learner, 48);
        runTask(learner, "QUIZ_GENERATION").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.details.remainingUnits").value(2));
        runTask(learner, "ASSISTANT_QUESTION").andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-04: người học ở múi giờ Việt Nam hết lượt, qua nửa đêm giờ Việt Nam thì lượt hồi đủ")
    void refillsAtLearnerMidnight() throws Exception {
        // Đưa đồng hồ tới mười phút trước nửa đêm giờ Việt Nam rồi mới tạo tài khoản: chạy lúc rạng sáng mà tua gần
        // một ngày thì phiên đăng nhập hết hạn trước khi kịp kiểm tra.
        ZoneId vietnam = ZoneId.of("Asia/Ho_Chi_Minh");
        Instant midnight = LocalDate.ofInstant(clock.instant(), vietnam).plusDays(1).atStartOfDay(vietnam).toInstant();
        Duration untilMidnight = Duration.between(clock.instant(), midnight);
        if (untilMidnight.compareTo(Duration.ofMinutes(10)) > 0) {
            clock.advance(untilMidnight.minusMinutes(10));
        }
        User learner = learner();
        setTimeZone(learner, "Asia/Ho_Chi_Minh");
        setUsage(learner, 50);
        runTask(learner, "ASSISTANT_QUESTION").andExpect(status().isTooManyRequests());
        Instant resetsAt = Instant.parse(JsonPath.read(mvc.perform(get("/api/v1/quota").cookie(learner.cookies()))
                .andReturn().getResponse().getContentAsString(), "$.ai.resetsAt"));

        // Một phút trước nửa đêm vẫn còn hết lượt.
        clock.advance(Duration.between(clock.instant(), resetsAt).minusMinutes(1));
        mvc.perform(get("/api/v1/quota").cookie(learner.cookies()))
                .andExpect(jsonPath("$.ai.remainingUnits").value(0));

        clock.advance(Duration.ofMinutes(2));
        mvc.perform(get("/api/v1/quota").cookie(learner.cookies()))
                .andExpect(jsonPath("$.ai.dailyUnits").value(50))
                .andExpect(jsonPath("$.ai.usedUnits").value(0))
                .andExpect(jsonPath("$.ai.remainingUnits").value(50))
                .andExpect(jsonPath("$.ai.studyDay").value(LocalDate.ofInstant(resetsAt,
                        ZoneId.of("Asia/Ho_Chi_Minh")).toString()));
        runTask(learner, "ASSISTANT_QUESTION").andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-04: nửa đêm tính theo múi giờ trong hồ sơ của từng người học")
    void midnightFollowsProfileZone() throws Exception {
        User learner = learner();
        setTimeZone(learner, "America/New_York");
        String body = mvc.perform(get("/api/v1/quota").cookie(learner.cookies()))
                .andExpect(jsonPath("$.ai.timeZone").value("America/New_York"))
                .andReturn().getResponse().getContentAsString();
        ZonedDateTime local = Instant.parse(JsonPath.read(body, "$.ai.resetsAt")).atZone(ZoneId.of("America/New_York"));
        assertThat(local.toLocalTime().toSecondOfDay()).isZero();
    }

    @Test
    @DisplayName("TC-05: người học gửi yêu cầu đổi hạn mức hay bảng quy đổi bị từ chối và ghi nhật ký; đồng hồ lượt vẫn "
            + "hiển thị bình thường")
    void learnerCannotChangePolicy() throws Exception {
        User learner = learner();
        mvc.perform(put("/api/v1/admin/quotas/policy").with(csrf()).cookie(learner.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dailyAiUnits\":9999,\"storageLimitMb\":99999}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(put("/api/v1/admin/quotas/costs").with(csrf()).cookie(learner.cookies())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"costs\":[{\"task\":\"QUIZ_GENERATION\",\"units\":1}]}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/quotas").cookie(learner.cookies())).andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'ROLE_ACCESS_DENIED' "
                + "and actor_id = ?::uuid", Long.class, learner.id())).isEqualTo(3);
        assertThat(jdbc.queryForObject("select daily_ai_units from quota_policies", Integer.class)).isEqualTo(50);
        assertThat(jdbc.queryForObject("select units from quota_task_costs where task = 'QUIZ_GENERATION'",
                Integer.class)).isEqualTo(5);
        mvc.perform(get("/api/v1/quota").cookie(learner.cookies())).andExpect(status().isOk())
                .andExpect(jsonPath("$.ai.remainingUnits").value(50));
    }

    @Test
    @DisplayName("TC-06: đổi hạn mức và bảng quy đổi ghi người thực hiện, giá trị trước sau và thời điểm; áp dụng ngay")
    void auditsChanges() throws Exception {
        User admin = admin();
        User learner = learner();
        Instant before = clock.instant();

        mvc.perform(put("/api/v1/admin/quotas/policy").with(csrf()).cookie(admin.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dailyAiUnits\":20,\"storageLimitMb\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policy.dailyAiUnits").value(20))
                .andExpect(jsonPath("$.policy.storageLimitBytes").value(500L * 1024 * 1024))
                .andExpect(jsonPath("$.policy.updatedBy.id").value(admin.id()))
                .andExpect(jsonPath("$.policy.updatedBy.displayName").value("Lan Anh"));
        mvc.perform(put("/api/v1/admin/quotas/costs").with(csrf()).cookie(admin.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"costs":[{"task":"QUIZ_GENERATION","units":8},{"task":"ASSISTANT_QUESTION","units":1}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costs[?(@.task == 'QUIZ_GENERATION')].units").value(8))
                .andExpect(jsonPath("$.costs[?(@.task == 'QUIZ_GENERATION')].defaultUnits").value(5))
                .andExpect(jsonPath("$.costs[?(@.task == 'QUIZ_GENERATION')].updatedBy.id").value(admin.id()));
        // Lưu lại đúng giá trị cũ thì không có gì để ghi.
        mvc.perform(put("/api/v1/admin/quotas/policy").with(csrf()).cookie(admin.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dailyAiUnits\":20,\"storageLimitMb\":500}"))
                .andExpect(status().isOk());

        List<Map<String, Object>> rows = jdbc.queryForList("""
                select action, actor_id::text as actor, object_type, details::text as details, occurred_at
                from audit_log where action in ('QUOTA_POLICY_CHANGED', 'QUOTA_TASK_COSTS_CHANGED')
                and actor_id = ?::uuid order by occurred_at""", admin.id());
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("action", "QUOTA_POLICY_CHANGED").containsEntry("actor", admin.id())
                .containsEntry("object_type", "QUOTA_POLICY");
        assertThat((String) rows.get(0).get("details")).contains("\"field\": \"dailyAiUnits\"")
                .contains("\"before\": 50").contains("\"after\": 20")
                .contains("\"field\": \"storageLimitMb\"").contains("\"before\": 1024").contains("\"after\": 500");
        assertThat(rows.get(1)).containsEntry("action", "QUOTA_TASK_COSTS_CHANGED");
        assertThat((String) rows.get(1).get("details")).contains("\"field\": \"QUIZ_GENERATION\"")
                .contains("\"before\": 5").contains("\"after\": 8").doesNotContain("ASSISTANT_QUESTION");
        assertThat(((java.sql.Timestamp) rows.get(0).get("occurred_at")).toInstant()).isAfterOrEqualTo(
                before.minusSeconds(1));

        // Hạn mức mới áp dụng ngay cho người học.
        mvc.perform(get("/api/v1/quota").cookie(learner.cookies()))
                .andExpect(jsonPath("$.ai.dailyUnits").value(20))
                .andExpect(jsonPath("$.costs[?(@.task == 'QUIZ_GENERATION')].units").value(8));
    }

    @Test
    @DisplayName("Hạn mức không hợp lệ bị từ chối: ít nhất một lượt mỗi ngày, mỗi tác vụ từ 1 đến 100 lượt")
    void validatesPolicy() throws Exception {
        User admin = admin();
        mvc.perform(put("/api/v1/admin/quotas/policy").with(csrf()).cookie(admin.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dailyAiUnits\":0,\"storageLimitMb\":500}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(put("/api/v1/admin/quotas/costs").with(csrf()).cookie(admin.cookies())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"costs\":[{\"task\":\"QUIZ_GENERATION\",\"units\":101}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/admin/quotas/costs").with(csrf()).cookie(admin.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"costs":[{"task":"QUIZ_GENERATION","units":2},{"task":"QUIZ_GENERATION","units":3}]}"""))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action like 'QUOTA_%' "
                + "and actor_id = ?::uuid", Long.class, admin.id())).isZero();
    }

    @Test
    @DisplayName("Nhiều tác vụ bắt đầu cùng lúc không bao giờ giữ chỗ vượt hạn mức")
    void concurrentReservationsNeverOverspend() throws Exception {
        User learner = learner();
        setUsage(learner, 47);
        UUID owner = UUID.fromString(learner.id());
        List<Callable<Boolean>> calls = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            calls.add(() -> {
                try {
                    reservations.reserve(owner, QuotaTask.ASSISTANT_QUESTION);
                    return true;
                } catch (BusinessException ex) {
                    assertThat(ex.code()).isEqualTo(ErrorCode.AI_QUOTA_EXHAUSTED);
                    return false;
                }
            });
        }
        ExecutorService pool = Executors.newFixedThreadPool(12);
        long granted = 0;
        try {
            for (Future<Boolean> result : pool.invokeAll(calls)) {
                granted += result.get() ? 1 : 0;
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(granted).isEqualTo(3);
        mvc.perform(get("/api/v1/quota").cookie(learner.cookies()))
                .andExpect(jsonPath("$.ai.reservedUnits").value(3))
                .andExpect(jsonPath("$.ai.remainingUnits").value(0));
    }

    @Test
    @DisplayName("Lần giữ chỗ treo quá ba mươi phút được tự hoàn lại, lượt trả về ngày đã giữ")
    void releasesStaleReservations() throws Exception {
        User learner = learner();
        UUID owner = UUID.fromString(learner.id());
        reservations.reserve(owner, QuotaTask.FLASHCARD_GENERATION);
        assertThat(staleJob.run()).isZero();
        clock.advance(Duration.ofMinutes(31));
        assertThat(staleJob.run()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select release_reason from quota_reservations where owner_id = ?::uuid",
                String.class, learner.id())).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("select reserved_units from quota_daily_usage where owner_id = ?::uuid",
                Integer.class, learner.id())).isZero();
    }

    @Test
    @DisplayName("Hạn mức dung lượng: tệp làm vượt hạn mức bị chặn, tài liệu đã có vẫn mở được")
    void storageQuota() throws Exception {
        User learner = learner();
        mvc.perform(post("/api/v1/dev/library/sample").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(learner.email()))).andExpect(status().isOk());
        String body = mvc.perform(get("/api/v1/quota").cookie(learner.cookies()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long used = ((Number) JsonPath.read(body, "$.storage.usedBytes")).longValue();
        assertThat(used).isPositive();

        jdbc.update("update quota_policies set storage_limit_bytes = ?", used + 10);
        mvc.perform(post("/api/v1/dev/library/sample").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(learner.email())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STORAGE_QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$.details.usedBytes").value(used));
        mvc.perform(get("/api/v1/library/documents").cookie(learner.cookies())).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/quota").cookie(learner.cookies()))
                .andExpect(jsonPath("$.storage.remainingBytes").value(10));
    }

    // ------------------------------------------------------------------ hỗ trợ

    private ResultActions runTask(User user, String task) throws Exception {
        return mvc.perform(post("/api/v1/dev/quota/tasks").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"task\":\"%s\"}".formatted(user.email(), task)));
    }

    private void setUsage(User user, int used) throws Exception {
        mvc.perform(post("/api/v1/dev/quota/usage").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"usedUnits\":%d}".formatted(user.email(), used)))
                .andExpect(status().isNoContent());
    }

    /** Đổi múi giờ qua hồ sơ như người học thật. */
    private void setTimeZone(User user, String zone) throws Exception {
        String profile = mvc.perform(get("/api/v1/profiles/me").cookie(user.cookies()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(profile, "$.id");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/profiles/" + id)
                        .with(csrf()).cookie(user.cookies()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeZone\":\"%s\"}".formatted(zone)))
                .andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder withBrowser(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.USER_AGENT, CHROME_ON_WINDOWS);
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\",\"rememberDevice\":false}".formatted(email, password);
    }

    private record User(String id, String email, List<Cookie> jar) {

        Cookie[] cookies() {
            return jar.toArray(Cookie[]::new);
        }
    }

    private static void absorb(List<Cookie> jar, MvcResult result) {
        for (String header : result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)) {
            String name = header.substring(0, header.indexOf('='));
            String value = header.substring(name.length() + 1, header.indexOf(';'));
            jar.removeIf(cookie -> cookie.getName().equals(name));
            if (!value.isEmpty() && !header.contains("Max-Age=0")) {
                jar.add(new Cookie(name, value));
            }
        }
    }

    /**
     * Quản trị viên duy nhất (NCL-03-CN-002): hạ quản trị viên cũ, cấp vai trò rồi đăng nhập đủ hai bước, vì phiên mở
     * bằng mật khẩu khi còn là người học không mang quyền quản trị.
     */
    private User admin() throws Exception {
        User learner = learner();
        jdbc.update("update user_accounts set role = 'LEARNER' where role = 'ADMIN'");
        jdbc.update("update user_accounts set role = 'ADMIN' where id = ?::uuid", learner.id());
        List<Cookie> jar = new ArrayList<>();
        absorb(jar, mvc.perform(withBrowser(post("/api/v1/auth/login")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(learner.email(), PASSWORD)))
                .andExpect(status().isAccepted()).andReturn());
        String setup = mvc.perform(post("/api/v1/auth/admin/two-factor/enrollment").with(csrf())
                        .cookie(jar.toArray(Cookie[]::new)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        byte[] secret = base32(JsonPath.read(setup, "$.secret"));
        String code = totp.codeAt(secret, TotpService.stepAt(clock.instant()));
        absorb(jar, mvc.perform(withBrowser(post("/api/v1/auth/admin/two-factor/enrollment/confirm")).with(csrf())
                        .cookie(jar.toArray(Cookie[]::new)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andReturn());
        User user = new User(learner.id(), learner.email(), jar);
        mvc.perform(get("/api/v1/admin/me").cookie(user.cookies())).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.email()));
        return user;
    }

    private static byte[] base32(String text) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : text.toCharArray()) {
            buffer = (buffer << 5) | alphabet.indexOf(c);
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    private User learner() throws Exception {
        String email = "han.muc." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Lan Anh","email":"%s","password":"%s","acceptTerms":true,
                                 "termsVersion":"2026-10-01","privacyVersion":"2026-10-01"}"""
                                .formatted(email, PASSWORD)))
                .andExpect(status().isAccepted());
        Matcher matcher = Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)")
                .matcher(String.join(" ", awaitMail(email).links()));
        assertThat(matcher.find()).isTrue();
        List<Cookie> jar = new ArrayList<>();
        absorb(jar, mvc.perform(post("/api/v1/auth/verify-email").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + matcher.group(1) + "\"}"))
                .andExpect(status().isOk()).andReturn());
        return withId(email, jar);
    }

    private User withId(String email, List<Cookie> jar) throws Exception {
        String session = mvc.perform(get("/api/v1/auth/session").cookie(jar.toArray(Cookie[]::new)))
                .andReturn().getResponse().getContentAsString();
        String id = session.replaceAll(".*\"account\":\\{\"id\":\"([^\"]+)\".*", "$1");
        return new User(id, email, jar);
    }

    private MockMailGateway.CapturedMail awaitMail(String to) {
        return await().atMost(Duration.ofSeconds(60))
                .until(() -> Optional.of(mailbox.messages().stream()
                                .filter(mail -> mail.to().equalsIgnoreCase(to) && mail.template().equals("VERIFY_EMAIL"))
                                .toList()).filter(list -> !list.isEmpty()), Optional::isPresent)
                .orElseThrow().get(0);
    }
}
