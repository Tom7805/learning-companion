package com.learningcompanion.ai.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.learningcompanion.admin.application.TotpService;
import com.learningcompanion.ai.config.application.AiConfigResolver;
import com.learningcompanion.ai.config.domain.AiFeature;
import com.learningcompanion.ai.gateway.AiGateway;
import com.learningcompanion.ai.gateway.model.ChatRequest;
import com.learningcompanion.ai.gateway.model.ChatResult;
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

/** NCL-04-CN-002 — cấu hình mô hình và lời nhắc hệ thống theo phiên bản, kiểm qua API và cơ sở dữ liệu thật. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class AiConfigIT {

    private static final String PASSWORD = "cau-hinh-tri-tue-2026";
    private static final String CHROME_ON_WINDOWS = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private static final String BASE = "/api/v1/admin/ai/configs/ASSISTANT_CHAT";
    private static final String BACKUP_ID = "6f1d0c1e-0000-4000-8000-000000000002";

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
    private AiConfigResolver resolver;

    @BeforeEach
    void freshConfigs() throws Exception {
        clock.reset();
        mvc.perform(post("/api/v1/dev/ai/reset?chunkDelayMs=0")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/dev/ai/configs/reset")).andExpect(status().isNoContent());
        jdbc.update("delete from ai_call_records");
    }

    @AfterEach
    void restore() throws Exception {
        mvc.perform(post("/api/v1/dev/ai/reset")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/dev/ai/configs/reset")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Mỗi chức năng được đăng ký sẵn phiên bản 1 đang phát hành, lấy từ giá trị gốc")
    void featuresAreRegistered() throws Exception {
        User admin = admin();

        mvc.perform(get("/api/v1/admin/ai/configs").cookie(admin.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features[0].feature").value("ASSISTANT_CHAT"))
                .andExpect(jsonPath("$.features[0].published.versionNumber").value(1))
                .andExpect(jsonPath("$.features[0].published.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.features[0].published.publishedBy").doesNotExist())
                .andExpect(jsonPath("$.sampleSets[0].sources").isArray());
        assertThat(resolver.resolve(AiFeature.ASSISTANT_CHAT).systemPrompt())
                .isEqualTo(AiFeature.ASSISTANT_CHAT.defaultSystemPrompt());
    }

    @Test
    @DisplayName("TC-01: sửa lời nhắc, thử câu hỏi mẫu rồi phát hành bản năm; lời gọi mới dùng bản năm, lịch sử ghi "
            + "người phát hành và thời điểm")
    void publishesVersionFive() throws Exception {
        User admin = admin();
        publishUpToVersionFour(admin);
        assertThat(resolver.resolve(AiFeature.ASSISTANT_CHAT).versionNumber()).isEqualTo(4);

        String promptFive = "Bạn là trợ lý học tập. Luôn trả lời bằng ba gạch đầu dòng và ghi nguồn [n].";
        save(admin, promptFive, 4).andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(5))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.basedOnVersion").value(4));
        // Chưa thử thì chưa phát hành được.
        publish(admin, 5).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_CONFIG_TRIAL_REQUIRED"));
        String stream = trial(admin, 5);
        assertThat(stream).contains("event:chunk").contains("\"version\":5");
        publish(admin, 5).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedBy.id").value(admin.id()))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty());

        // Mọi lời gọi mới của chức năng chạy theo phiên bản năm.
        ChatRequest request = resolver.chatRequest(AiFeature.ASSISTANT_CHAT, "Định luật Ohm là gì?");
        assertThat(request.system()).isEqualTo(promptFive);
        assertThat(request.tuning().configVersion()).isEqualTo(5);
        jdbc.update("delete from ai_call_records");
        gateway.streamText(AiFeature.ASSISTANT_CHAT.name(), request, chunk -> {
        });
        assertThat(jdbc.queryForObject("select config_version from ai_call_records where feature = 'ASSISTANT_CHAT'",
                Integer.class)).isEqualTo(5);

        mvc.perform(get(BASE + "/versions").cookie(admin.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].versionNumber").value(5))
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$[0].publishedBy.displayName").value("Lan Anh"))
                .andExpect(jsonPath("$[1].versionNumber").value(4))
                .andExpect(jsonPath("$[1].status").value("SUPERSEDED"));
        assertThat(jdbc.queryForObject("select count(*) from ai_config_versions where feature = 'ASSISTANT_CHAT' "
                + "and status = 'PUBLISHED'", Long.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-02: phiên bản năm có sự cố, bấm quay lại thì phiên bản bốn chạy lại ngay và bản năm bị thu hồi")
    void rollsBackToVersionFour() throws Exception {
        User admin = admin();
        publishUpToVersionFour(admin);
        save(admin, "Trả lời bằng định dạng JSON sai.", 4).andExpect(status().isCreated());
        trial(admin, 5);
        publish(admin, 5).andExpect(status().isOk());

        mvc.perform(post(BASE + "/rollback").with(csrf()).cookie(admin.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(4))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        assertThat(resolver.resolve(AiFeature.ASSISTANT_CHAT).versionNumber()).isEqualTo(4);
        Map<String, Object> five = jdbc.queryForMap("select status, revoked_by::text as revoked_by, revoked_at "
                + "from ai_config_versions where feature = 'ASSISTANT_CHAT' and version_number = 5");
        assertThat(five.get("status")).isEqualTo("REVOKED");
        assertThat(five.get("revoked_by")).isEqualTo(admin.id());
        assertThat(five.get("revoked_at")).isNotNull();
        // Bản đã thu hồi không phát hành lại được; muốn dùng lại phải lưu thành bản nháp mới.
        publish(admin, 5).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_CONFIG_NOT_PUBLISHABLE"));
        // Quay lại tiếp thì về bản phát hành trước nữa.
        mvc.perform(post(BASE + "/rollback").with(csrf()).cookie(admin.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(3));
    }

    @Test
    @DisplayName("TC-03: lời nhắc nháp có địa chỉ thư thì bị chặn lưu và đoạn chứa dữ liệu cá nhân được đánh dấu")
    void blocksPromptWithEmail() throws Exception {
        User admin = admin();
        String prompt = "Bạn là trợ lý. Nếu học sinh cần, hãy bảo các em viết cho thay.minh@truong.edu.vn.";

        MvcResult rejected = save(admin, prompt, 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_CONFIG_PROMPT_HAS_PII"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("systemPrompt"))
                .andExpect(jsonPath("$.details.findings[0].kind").value("EMAIL"))
                .andReturn();
        String body = rejected.getResponse().getContentAsString();
        int start = JsonPath.read(body, "$.details.findings[0].start");
        int end = JsonPath.read(body, "$.details.findings[0].end");
        assertThat(prompt.substring(start, end)).isEqualTo("thay.minh@truong.edu.vn");

        assertThat(jdbc.queryForObject("select count(*) from ai_config_versions where feature = 'ASSISTANT_CHAT'",
                Long.class)).isEqualTo(1);
        String details = jdbc.queryForObject("select details::text from audit_log where action = "
                + "'AI_CONFIG_SAVE_REJECTED' and actor_id = ?::uuid", String.class, admin.id());
        assertThat(details).contains("EMAIL").doesNotContain("truong.edu.vn").doesNotContain("thay.minh");
    }

    @Test
    @DisplayName("TC-04: người không thuộc vai trò quản trị viên bị từ chối và lần từ chối được ghi nhật ký")
    void learnerIsDenied() throws Exception {
        User learner = learner();

        mvc.perform(get("/api/v1/admin/ai/configs").cookie(learner.cookies()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post(BASE + "/rollback").with(csrf()).cookie(learner.cookies()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/ai/configs")).andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'ROLE_ACCESS_DENIED' "
                + "and actor_id = ?::uuid", Long.class, learner.id())).isEqualTo(2);
        assertThat(resolver.resolve(AiFeature.ASSISTANT_CHAT).versionNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-05: lưu, phát hành và quay lại đều ghi người thực hiện, nội dung và thời điểm")
    void auditsEveryChange() throws Exception {
        User admin = admin();
        save(admin, "Bạn là trợ lý.\nTrả lời ngắn gọn.", 1).andExpect(status().isCreated());
        trial(admin, 2);
        publish(admin, 2).andExpect(status().isOk());
        mvc.perform(post(BASE + "/rollback").with(csrf()).cookie(admin.cookies())).andExpect(status().isOk());

        List<Map<String, Object>> rows = jdbc.queryForList("select action, actor_id::text as actor, occurred_at, "
                + "object_type, details::text as details from audit_log where actor_id = ?::uuid and action like "
                + "'AI_CONFIG_%' order by occurred_at, action", admin.id());
        assertThat(rows).extracting(row -> row.get("action"))
                .containsExactlyInAnyOrder("AI_CONFIG_DRAFT_SAVED", "AI_CONFIG_PUBLISHED", "AI_CONFIG_ROLLED_BACK");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("actor")).isEqualTo(admin.id());
            assertThat(row.get("occurred_at")).isNotNull();
            assertThat(row.get("object_type")).isEqualTo("AI_CONFIG");
            assertThat(row.get("details").toString()).contains("ASSISTANT_CHAT");
        });
        Map<String, String> byAction = new java.util.HashMap<>();
        rows.forEach(row -> byAction.put(row.get("action").toString(), row.get("details").toString()));
        assertThat(byAction.get("AI_CONFIG_DRAFT_SAVED")).contains("\"version\": 2")
                .contains("\"basedOnVersion\": 1").contains("temperature").contains("instructionLinesAdded")
                .doesNotContain("Trả lời ngắn gọn");
        assertThat(byAction.get("AI_CONFIG_PUBLISHED")).contains("\"version\": 2")
                .contains("\"previousVersion\": 1");
        assertThat(byAction.get("AI_CONFIG_ROLLED_BACK")).contains("\"fromVersion\": 2")
                .contains("\"toVersion\": 1");
    }

    @Test
    @DisplayName("Nhà cung cấp và mô hình trong cấu hình được dùng thay định tuyến; nhà cung cấp tắt thì không phát hành")
    void usesConfiguredProvider() throws Exception {
        User admin = admin();
        mvc.perform(post(BASE + "/versions").with(csrf()).cookie(admin.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"providerCode":"mock-backup","model":"mo-hinh-thu","temperature":0.7,
                                 "maxOutputTokens":800,"systemPrompt":"Bạn là trợ lý.","basedOnVersion":1}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.providerCode").value("mock-backup"))
                .andExpect(jsonPath("$.temperature").value(0.7));
        assertThat(trial(admin, 2)).contains("\"providerCode\":\"mock-backup\"");

        mvc.perform(post("/api/v1/admin/ai/providers/" + BACKUP_ID + "/disable").with(csrf())
                        .cookie(admin.cookies()))
                .andExpect(status().isOk());
        publish(admin, 2).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_CONFIG_PROVIDER_UNAVAILABLE"));

        mvc.perform(post("/api/v1/admin/ai/providers/" + BACKUP_ID + "/enable").with(csrf())
                        .cookie(admin.cookies()))
                .andExpect(status().isOk());
        publish(admin, 2).andExpect(status().isOk());
        ChatResult result = gateway.streamText(AiFeature.ASSISTANT_CHAT.name(),
                resolver.chatRequest(AiFeature.ASSISTANT_CHAT, "Xin chào"), chunk -> {
                });
        assertThat(result.providerCode()).isEqualTo("mock-backup");

        // Nhà cung cấp không có hoặc mô hình thiếu nhà cung cấp thì không lưu được.
        mvc.perform(post(BASE + "/versions").with(csrf()).cookie(admin.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"providerCode":"khong-co","temperature":0.2,"maxOutputTokens":800,
                                 "systemPrompt":"Bạn là trợ lý."}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_CONFIG_PROVIDER_INVALID"));
        mvc.perform(post(BASE + "/versions").with(csrf()).cookie(admin.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"model":"mo-hinh","temperature":3,"maxOutputTokens":0,"systemPrompt":" "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Xem khác biệt giữa hai phiên bản: tham số đổi và lời nhắc theo từng dòng")
    void diffsTwoVersions() throws Exception {
        User admin = admin();
        save(admin, "Bạn là trợ lý.\nTrả lời ngắn gọn.", 1).andExpect(status().isCreated());
        save(admin, "Bạn là trợ lý.\nTrả lời đầy đủ, có ví dụ.", 2, "0.9").andExpect(status().isCreated());

        mvc.perform(get(BASE + "/diff?from=2&to=3").cookie(admin.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[0].field").value("temperature"))
                .andExpect(jsonPath("$.fields[0].before").value("0.4"))
                .andExpect(jsonPath("$.fields[0].after").value("0.9"))
                .andExpect(jsonPath("$.promptLines[0].change").value("SAME"))
                .andExpect(jsonPath("$.promptLines[1].change").value("REMOVED"))
                .andExpect(jsonPath("$.promptLines[1].text").value("Trả lời ngắn gọn."))
                .andExpect(jsonPath("$.promptLines[2].change").value("ADDED"));
        mvc.perform(get(BASE + "/diff?from=2&to=99").cookie(admin.cookies()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Lời gọi thử của lớp kết nối đọc lời nhắc từ cấu hình đang phát hành của chức năng ADMIN_TEST")
    void gatewayTestCallUsesConfig() throws Exception {
        User admin = admin();
        MvcResult started = mvc.perform(post("/api/v1/admin/ai/test-call").with(csrf()).cookie(admin.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"Xin chào\"}"))
                .andExpect(status().isOk()).andReturn();
        await().atMost(Duration.ofSeconds(10)).until(() -> started.getResponse().getContentAsString()
                .contains("event:done"));
        assertThat(jdbc.queryForObject("select config_version from ai_call_records where feature = 'ADMIN_TEST'",
                Integer.class)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- hỗ trợ

    /** Lưu, thử rồi phát hành lần lượt các phiên bản 2, 3, 4 để phiên bản bốn đang chạy như trong tiêu chí. */
    private void publishUpToVersionFour(User admin) throws Exception {
        for (int number = 2; number <= 4; number++) {
            save(admin, "Bạn là trợ lý học tập, phiên bản " + number + ".", number - 1)
                    .andExpect(status().isCreated());
            trial(admin, number);
            publish(admin, number).andExpect(status().isOk());
        }
    }

    private ResultActions save(User admin, String prompt, int basedOn) throws Exception {
        return save(admin, prompt, basedOn, "0.4");
    }

    private ResultActions save(User admin, String prompt, int basedOn, String temperature) throws Exception {
        String body = """
                {"temperature":%s,"maxOutputTokens":900,"systemPrompt":%s,"basedOnVersion":%d}"""
                .formatted(temperature, quote(prompt), basedOn);
        return mvc.perform(post(BASE + "/versions").with(csrf()).cookie(admin.cookies())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions publish(User admin, int number) throws Exception {
        return mvc.perform(post(BASE + "/versions/" + number + "/publish").with(csrf()).cookie(admin.cookies()));
    }

    private String trial(User admin, int number) throws Exception {
        MvcResult started = mvc.perform(post(BASE + "/versions/" + number + "/trial").with(csrf())
                        .cookie(admin.cookies()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Định luật Ohm nói gì?\",\"sampleSetId\":\"physics\"}"))
                .andExpect(status().isOk()).andReturn();
        await().atMost(Duration.ofSeconds(10)).until(() -> started.getResponse().getContentAsString()
                .contains("event:done"));
        // Mốc thử được ghi sau khi luồng kết thúc; chờ để phát hành ngay sau đó không bị từ chối.
        await().atMost(Duration.ofSeconds(5)).until(() -> jdbc.queryForObject("select last_trial_at is not null "
                + "from ai_config_versions where feature = 'ASSISTANT_CHAT' and version_number = ?", Boolean.class,
                number));
        return started.getResponse().getContentAsString();
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
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
