package com.learningcompanion.ai.gateway;

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
import com.learningcompanion.ai.gateway.model.ChatChunk;
import com.learningcompanion.ai.gateway.model.ChatRequest;
import com.learningcompanion.ai.gateway.model.ChatResult;
import com.learningcompanion.ai.gateway.model.EmbeddingRequest;
import com.learningcompanion.ai.gateway.model.EmbeddingResult;
import com.learningcompanion.ai.provider.mock.MockAiAdapter;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.mail.MockMailGateway;
import com.learningcompanion.shared.time.AdjustableClock;
import com.learningcompanion.support.PostgresTestConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
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

/** NCL-04-CN-001 — lớp kết nối dịch vụ trí tuệ nhân tạo thay được nhà cung cấp, kiểm qua API và cơ sở dữ liệu thật. */
@SpringBootTest(properties = "app.ai.secrets.LC_AI_TEST_KEY=sk-kiem-thu-khong-duoc-lo")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class AiGatewayIT {

    private static final String PASSWORD = "ket-noi-tri-tue-2026";
    private static final String CHROME_ON_WINDOWS = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private static final String PRIMARY_ID = "6f1d0c1e-0000-4000-8000-000000000001";

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
    private AiGateway gateway;

    @Autowired
    private MockAiAdapter mock;

    @BeforeEach
    void freshGateway() throws Exception {
        clock.reset();
        mvc.perform(post("/api/v1/dev/ai/reset?chunkDelayMs=0")).andExpect(status().isNoContent());
        jdbc.update("delete from ai_call_records");
    }

    @AfterEach
    void restore() throws Exception {
        mvc.perform(post("/api/v1/dev/ai/reset")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("TC-01: sinh văn bản trả theo luồng, lời gọi được đo đơn vị, độ trễ, chi phí mà không ghi nội dung")
    void streamsAndMetersWithoutContent() {
        String secretPrompt = "Câu hỏi bí mật " + UUID.randomUUID();
        List<String> chunks = new CopyOnWriteArrayList<>();

        ChatResult result = gateway.streamText("ASSISTANT_CHAT", ChatRequest.of("Trả lời ngắn.", secretPrompt),
                chunk -> chunks.add(chunk.text()));

        assertThat(chunks).hasSizeGreaterThan(3);
        assertThat(String.join("", chunks)).isEqualTo(result.text());
        assertThat(result.providerCode()).isEqualTo("mock-primary");
        Map<String, Object> row = jdbc.queryForMap("select * from ai_call_records");
        assertThat(row.get("capability")).isEqualTo("TEXT_GENERATION");
        assertThat(row.get("feature")).isEqualTo("ASSISTANT_CHAT");
        assertThat(row.get("provider_code")).isEqualTo("mock-primary");
        assertThat(row.get("outcome")).isEqualTo("SUCCESS");
        assertThat(row.get("used_fallback")).isEqualTo(false);
        assertThat((Integer) row.get("input_units")).isEqualTo(result.usage().inputUnits()).isPositive();
        assertThat((Integer) row.get("output_units")).isEqualTo(chunks.size());
        assertThat((Integer) row.get("latency_ms")).isNotNegative();
        // Giá mô phỏng chính: 0,5 và 1,5 đô la mỗi triệu đơn vị, lưu bằng phần triệu đô la.
        assertThat((Long) row.get("cost_micro_usd")).isEqualTo(
                Math.round(result.usage().inputUnits() * 0.5 + result.usage().outputUnits() * 1.5));
        assertThat(row.values()).noneMatch(value -> value != null && value.toString().contains("bí mật"));
        assertThat(row.keySet()).noneMatch(column -> column.contains("content") || column.contains("prompt")
                || column.contains("text"));
    }

    @Test
    @DisplayName("TC-01: biểu diễn ngữ nghĩa qua lớp kết nối cho kết quả cố định ở môi trường thử")
    void embeddingsAreDeterministic() {
        EmbeddingResult first = gateway.embed("LIBRARY_INDEX", new EmbeddingRequest(List.of("định luật Ohm", "a")));
        EmbeddingResult second = gateway.embed("LIBRARY_INDEX", new EmbeddingRequest(List.of("định luật Ohm")));

        assertThat(first.vectors()).hasSize(2);
        assertThat(first.vectors().getFirst()).containsExactly(second.vectors().getFirst());
        assertThat(jdbc.queryForObject("select count(*) from ai_call_records where capability = 'EMBEDDING'",
                Long.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("TC-02: năm lỗi liên tiếp thì ngắt mạch, chuyển dự phòng và quản trị viên nhận cảnh báo")
    void opensCircuitAndFailsOver() throws Exception {
        User admin = admin();
        mock.setMode("mock-primary", MockAiAdapter.Mode.FAIL);

        // Mỗi lời gọi thử lại một lần: hai lời gọi đầu là bốn lỗi, chức năng gọi nhận lỗi tạm thời.
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> callText())
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.code()).isEqualTo(ErrorCode.AI_TEMPORARILY_UNAVAILABLE));
        }
        // Lỗi thứ năm ngắt mạch: lời gọi chuyển sang dự phòng và vẫn trả kết quả.
        assertThat(callText().providerCode()).isEqualTo("mock-backup");
        // Lời gọi mới đi thẳng sang dự phòng, không chạm nhà cung cấp chính.
        assertThat(callText().providerCode()).isEqualTo("mock-backup");

        List<Map<String, Object>> rows = jdbc.queryForList(
                "select provider_code, outcome, used_fallback, attempts from ai_call_records order by occurred_at");
        assertThat(rows).hasSize(4);
        assertThat(rows.get(2)).containsEntry("provider_code", "mock-backup").containsEntry("used_fallback", true)
                .containsEntry("attempts", 2);
        assertThat(rows.get(3)).containsEntry("used_fallback", true).containsEntry("attempts", 1);

        mvc.perform(get("/api/v1/admin/ai/gateway").cookie(admin.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts.length()").value(1))
                .andExpect(jsonPath("$.alerts[0].capability").value("TEXT_GENERATION"))
                .andExpect(jsonPath("$.alerts[0].providerCode").value("mock-primary"))
                .andExpect(jsonPath("$.alerts[0].fallbackProviderCode").value("mock-backup"))
                .andExpect(jsonPath("$.routes[?(@.capability == 'TEXT_GENERATION')].primary.circuit").value("OPEN"));
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'AI_CIRCUIT_OPENED' "
                + "and actor_type = 'SYSTEM' and details ->> 'fallbackProvider' = 'mock-backup'", Long.class))
                .isPositive();
        MockMailGateway.CapturedMail mail = await().atMost(Duration.ofSeconds(30))
                .until(() -> mailbox.messages().stream()
                        .filter(m -> m.to().equalsIgnoreCase(admin.email()) && m.template().equals("AI_CIRCUIT_OPEN"))
                        .findFirst(), Optional::isPresent).orElseThrow();
        assertThat(mail.html()).contains("mock-primary").contains("mock-backup");

        // Một mạch chỉ cảnh báo một lần cho tới khi quản trị viên xác nhận.
        String alertId = JsonPath.read(mvc.perform(get("/api/v1/admin/ai/gateway").cookie(admin.cookies()))
                .andReturn().getResponse().getContentAsString(), "$.alerts[0].id");
        mvc.perform(post("/api/v1/admin/ai/alerts/" + alertId + "/acknowledge").with(csrf()).cookie(admin.cookies()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/admin/ai/gateway").cookie(admin.cookies()))
                .andExpect(jsonPath("$.alerts.length()").value(0));
    }

    @Test
    @DisplayName("TC-03: nhà cung cấp treo thì dừng chờ, thử lại một lần rồi trả lỗi tạm thời")
    void timesOutRetriesOnceThenFails() throws Exception {
        User admin = admin();
        updateRoute(admin, "TEXT_GENERATION", 1, 1).andExpect(status().isOk());
        mock.setMode("mock-primary", MockAiAdapter.Mode.HANG);

        long started = System.nanoTime();
        assertThatThrownBy(this::callText).isInstanceOfSatisfying(BusinessException.class, ex -> {
            assertThat(ex.code()).isEqualTo(ErrorCode.AI_TEMPORARILY_UNAVAILABLE);
            assertThat(ex.details()).containsEntry("retryable", true);
        });
        long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertThat(elapsedMs).isBetween(1_900L, 10_000L);
        Map<String, Object> row = jdbc.queryForMap("select * from ai_call_records");
        assertThat(row).containsEntry("outcome", "TIMEOUT").containsEntry("attempts", 2)
                .containsEntry("error_code", "TIMEOUT").containsEntry("used_fallback", false);
    }

    @Test
    @DisplayName("TC-04: bật nhà cung cấp chưa xác nhận không huấn luyện bị từ chối và nêu điều kiện")
    void refusesProviderWithoutNoTrainingConfirmation() throws Exception {
        User admin = admin();
        String code = "openai-" + UUID.randomUUID().toString().substring(0, 6);
        String created = mvc.perform(post("/api/v1/admin/ai/providers").with(csrf()).cookie(admin.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content(provider(code, false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.secretConfigured").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(created).doesNotContain("sk-kiem-thu");
        String id = JsonPath.read(created, "$.id");

        mvc.perform(post("/api/v1/admin/ai/providers/" + id + "/enable").with(csrf()).cookie(admin.cookies()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_PROVIDER_TRAINING_NOT_CONFIRMED"))
                .andExpect(jsonPath("$.details.missing[0]").value("NO_TRAINING_CONFIRMATION"));
        assertThat(jdbc.queryForObject("select enabled from ai_providers where id = ?::uuid", Boolean.class, id))
                .isFalse();
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'AI_PROVIDER_ENABLE_REJECTED' "
                + "and actor_id = ?::uuid and object_id = ?::uuid", Long.class, admin.id(), id)).isEqualTo(1);

        // Xác nhận cam kết rồi thì bật được.
        mvc.perform(put("/api/v1/admin/ai/providers/" + id).with(csrf()).cookie(admin.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content(provider(code, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noTrainingConfirmed").value(true));
        mvc.perform(post("/api/v1/admin/ai/providers/" + id + "/enable").with(csrf()).cookie(admin.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    @DisplayName("TC-04: khóa bí mật thiếu trong kho thì không bật được; giá trị khóa không bao giờ trả về")
    void refusesProviderWithoutSecret() throws Exception {
        User admin = admin();
        String code = "missing-" + UUID.randomUUID().toString().substring(0, 6);
        String body = provider(code, true).replace("LC_AI_TEST_KEY", "LC_AI_KHONG_CO");
        String id = JsonPath.read(mvc.perform(post("/api/v1/admin/ai/providers").with(csrf()).cookie(admin.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.secretConfigured").value(false))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/api/v1/admin/ai/providers/" + id + "/enable").with(csrf()).cookie(admin.cookies()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_PROVIDER_SECRET_MISSING"));
    }

    @Test
    @DisplayName("TC-05: người học mở lớp kết nối bị từ chối và lần từ chối được ghi nhật ký")
    void learnerIsDenied() throws Exception {
        User learner = learner();

        mvc.perform(get("/api/v1/admin/ai/gateway").cookie(learner.cookies()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/v1/admin/ai/routes/TEXT_GENERATION/switch").with(csrf()).cookie(learner.cookies()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/ai/gateway")).andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'ROLE_ACCESS_DENIED' "
                + "and actor_id = ?::uuid", Long.class, learner.id())).isEqualTo(2);
    }

    @Test
    @DisplayName("TC-06: đổi định tuyến và chuyển dự phòng được ghi người thực hiện, nội dung và thời điểm")
    void recordsRouteChangesAndFailover() throws Exception {
        User admin = admin();

        updateRoute(admin, "OCR", 20, 2).andExpect(status().isOk())
                .andExpect(jsonPath("$.timeoutSeconds").value(20));
        mvc.perform(post("/api/v1/admin/ai/routes/OCR/switch").with(csrf()).cookie(admin.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primary.code").value("mock-backup"))
                .andExpect(jsonPath("$.fallback.code").value("mock-primary"));

        Map<String, Object> change = jdbc.queryForMap("select actor_id::text as actor, occurred_at, "
                + "details::text as details from audit_log where action = 'AI_ROUTE_CHANGED' and actor_id = ?::uuid",
                admin.id());
        assertThat(change.get("occurred_at")).isNotNull();
        assertThat(change.get("details").toString()).contains("\"capability\": \"OCR\"")
                .contains("timeoutSeconds").contains("maxRetries");
        Map<String, Object> failover = jdbc.queryForMap("select details::text as details from audit_log "
                + "where action = 'AI_FAILOVER_SWITCHED' and actor_id = ?::uuid", admin.id());
        assertThat(failover.get("details").toString()).contains("mock-primary").contains("mock-backup");

        // Lời gọi nhận dạng chữ giờ đi tới nhà cung cấp mới làm chính.
        assertThat(gateway.recognizeText("LIBRARY_OCR", new com.learningcompanion.ai.gateway.model.VisionRequest(
                new byte[] {1, 2, 3}, "image/png", null)).providerCode()).isEqualTo("mock-backup");
    }

    @Test
    @DisplayName("Nhà cung cấp chính đang dùng thì không tắt được; định tuyến sai khả năng bị từ chối")
    void guardsRoutes() throws Exception {
        User admin = admin();
        mvc.perform(post("/api/v1/admin/ai/providers/" + PRIMARY_ID + "/disable").with(csrf())
                        .cookie(admin.cookies()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_PROVIDER_IN_USE"));
        mvc.perform(put("/api/v1/admin/ai/routes/TEXT_GENERATION").with(csrf()).cookie(admin.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"primaryProviderId":"%s","fallbackProviderId":"%s","timeoutSeconds":30,
                                 "maxRetries":1,"breakerFailureThreshold":5,"breakerWindowSeconds":60,
                                 "breakerOpenSeconds":60}""".formatted(PRIMARY_ID, PRIMARY_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_ROUTE_INVALID"));
    }

    @Test
    @DisplayName("Lời gọi thử từ trang quản trị trả theo luồng sự kiện và được đo riêng")
    void adminTestCallStreams() throws Exception {
        User admin = admin();
        MvcResult started = mvc.perform(post("/api/v1/admin/ai/test-call").with(csrf()).cookie(admin.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"Xin chào\"}"))
                .andExpect(status().isOk()).andReturn();
        await().atMost(Duration.ofSeconds(10)).until(() -> started.getResponse().getContentAsString()
                .contains("event:done"));
        String stream = started.getResponse().getContentAsString();
        assertThat(stream).contains("event:chunk").contains("\"providerCode\":\"mock-primary\"");
        assertThat(jdbc.queryForObject("select feature from ai_call_records", String.class)).isEqualTo("ADMIN_TEST");
    }

    // ---------------------------------------------------------------- hỗ trợ

    private ChatResult callText() {
        return gateway.streamText("ASSISTANT_CHAT", ChatRequest.of(null, "Định luật Ohm là gì?"),
                (ChatChunk chunk) -> {
                });
    }

    private ResultActions updateRoute(User admin, String capability, int timeoutSeconds, int maxRetries)
            throws Exception {
        return mvc.perform(put("/api/v1/admin/ai/routes/" + capability).with(csrf()).cookie(admin.cookies())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"primaryProviderId":"%s","fallbackProviderId":"6f1d0c1e-0000-4000-8000-000000000002",
                         "timeoutSeconds":%d,"maxRetries":%d,"breakerFailureThreshold":5,
                         "breakerWindowSeconds":60,"breakerOpenSeconds":60}"""
                        .formatted(PRIMARY_ID, timeoutSeconds, maxRetries)));
    }

    private static String provider(String code, boolean confirmed) {
        return """
                {"code":"%s","displayName":"Dịch vụ thử","adapter":"OPENAI_COMPATIBLE",
                 "baseUrl":"https://ai.example.com/v1","textModel":"model-thu","embeddingModel":"embed-thu",
                 "capabilities":["TEXT_GENERATION","EMBEDDING"],"secretName":"LC_AI_TEST_KEY",
                 "inputPricePerMillion":0.15,"outputPricePerMillion":0.6,"noTrainingConfirmed":%s}"""
                .formatted(code, confirmed);
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
        String email = "ket.noi." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
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
