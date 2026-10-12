package com.learningcompanion.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import tools.jackson.databind.ObjectMapper;

/** NCL-01-CN-006 — điều khoản, chính sách quyền riêng tư và đồng ý theo mục đích, kiểm qua API. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class ConsentIT {

    private static final String PASSWORD = "song-xanh-mua-thu-2026";
    private static final ObjectMapper JSON = new ObjectMapper();
    /** Phiên bản thử có hiệu lực rất xa trong tương lai rồi tua đồng hồ tới, để không ảnh hưởng các bài khác. */
    private static final Duration FAR_FUTURE = Duration.ofDays(3650);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MockMailGateway mailbox;

    @Autowired
    private AdjustableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    @DisplayName("Mục đích không bắt buộc đều tắt sẵn, trang quyền riêng tư nêu văn bản đang áp dụng kèm điểm chính")
    void optionalPurposesStartOff() throws Exception {
        Learner lan = learner();

        privacyOf(lan).andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptanceRequired").value(false))
                .andExpect(jsonPath("$.acceptedTermsVersion").value("2026-10-01"))
                .andExpect(jsonPath("$.privacy.version").value("2026-10-01"))
                .andExpect(jsonPath("$.privacy.highlights[0]").value(org.hamcrest.Matchers.containsString("thuộc về bạn")))
                .andExpect(jsonPath("$.purposes[0].purpose").value("ANONYMOUS_USAGE_ANALYTICS"))
                .andExpect(jsonPath("$.purposes[0].granted").value(false))
                .andExpect(jsonPath("$.purposes[1].purpose").value("PRODUCT_NEWS_EMAIL"))
                .andExpect(jsonPath("$.purposes[1].granted").value(false));
    }

    @Test
    @DisplayName("TC-01: bật thống kê ẩn danh thì đồng ý được ghi kèm phiên bản chính sách, thống kê bắt đầu từ lần sau")
    void enablingAnalyticsStartsFromNextAction() throws Exception {
        Learner lan = learner();

        event(lan, "/library/" + UUID.randomUUID()).andExpect(jsonPath("$.recorded").value(false));
        long before = usageEvents();

        setConsent(lan, "ANONYMOUS_USAGE_ANALYTICS", true).andExpect(status().isOk())
                .andExpect(jsonPath("$.purposes[0].granted").value(true))
                .andExpect(jsonPath("$.purposes[0].documentVersion").value("2026-10-01"))
                .andExpect(jsonPath("$.purposes[0].updatedAt").exists());

        String id = UUID.randomUUID().toString();
        event(lan, "/library/" + id + "?q=bi-mat").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.recorded").value(true));
        assertThat(usageEvents()).isEqualTo(before + 1);
        // Ẩn danh: không mã tài khoản, không mã bản ghi, không chuỗi truy vấn.
        assertThat(jdbc.queryForList("select route from usage_events", String.class))
                .contains("/library/:id").noneMatch(route -> route.contains(id) || route.contains("bi-mat"));

        history(lan).andExpect(jsonPath("$[0].purpose").value("ANONYMOUS_USAGE_ANALYTICS"))
                .andExpect(jsonPath("$[0].granted").value(true))
                .andExpect(jsonPath("$[0].documentVersion").value("2026-10-01"))
                .andExpect(jsonPath("$[0].recordedAt").exists());
    }

    @Test
    @DisplayName("TC-02: tắt thư giới thiệu tính năng mới thì ngừng gửi ngay và lần rút lại được ghi vào lịch sử")
    void withdrawingProductNewsStopsImmediately() throws Exception {
        Learner lan = learner();
        setConsent(lan, "PRODUCT_NEWS_EMAIL", true).andExpect(jsonPath("$.purposes[1].granted").value(true));

        sendNews("Sổ tay mới");
        MockMailGateway.CapturedMail news = awaitMail(lan.email(), "PRODUCT_NEWS", 1);
        assertThat(news.html()).contains("Sổ tay mới").contains("/privacy");

        setConsent(lan, "PRODUCT_NEWS_EMAIL", false).andExpect(jsonPath("$.purposes[1].granted").value(false));
        sendNews("Thẻ ghi nhớ mới");
        Thread.sleep(500);
        assertThat(mails(lan.email(), "PRODUCT_NEWS")).hasSize(1);

        history(lan).andExpect(jsonPath("$[0].purpose").value("PRODUCT_NEWS_EMAIL"))
                .andExpect(jsonPath("$[0].granted").value(false))
                .andExpect(jsonPath("$[1].granted").value(true));
        // Gửi lại đúng trạng thái đang có thì không ghi thêm lịch sử.
        setConsent(lan, "PRODUCT_NEWS_EMAIL", false);
        assertThat(JSON.readTree(history(lan).andReturn().getResponse().getContentAsString()).size()).isEqualTo(4);
    }

    @Test
    @DisplayName("Mục đích bắt buộc không tắt được qua trang quyền riêng tư")
    void requiredPurposesCannotBeWithdrawnHere() throws Exception {
        Learner lan = learner();
        setConsent(lan, "TERMS_OF_SERVICE", false).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSENT_PURPOSE_REQUIRED"));
        setConsent(lan, "KHONG_CO", true).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-03: điều khoản lên phiên bản mới thì phải đồng ý trước khi dùng tiếp, dữ liệu giữ nguyên")
    void newTermsVersionMustBeAcceptedBeforeContinuing() throws Exception {
        Learner lan = learner();
        String document = sample(lan.email());
        String version = "e2e-" + UUID.randomUUID().toString().substring(0, 8);
        publish("TERMS_OF_SERVICE", version, List.of("Thêm điều khoản về trợ lý học tập.", "Làm rõ quyền xóa dữ liệu."));
        clock.advance(FAR_FUTURE.plusSeconds(1));
        Learner later = login(lan.email());

        privacyOf(later).andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptanceRequired").value(true))
                .andExpect(jsonPath("$.terms.version").value(version))
                .andExpect(jsonPath("$.terms.changes[0]").value("Thêm điều khoản về trợ lý học tập."));
        mvc.perform(get("/api/v1/library/documents/" + document).cookie(later.cookies()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TERMS_ACCEPTANCE_REQUIRED"));
        mvc.perform(get("/api/v1/profiles/me").cookie(later.cookies())).andExpect(status().isOk());

        accept(later, "2025-01-01", "2026-10-01").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TERMS_VERSION_OUTDATED"));
        accept(later, version, "2026-10-01").andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptanceRequired").value(false))
                .andExpect(jsonPath("$.acceptedTermsVersion").value(version));

        mvc.perform(get("/api/v1/library/documents/" + document).cookie(later.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentText").value(org.hamcrest.Matchers.containsString("Ohm")));
        history(later).andExpect(jsonPath("$[0].purpose").value("TERMS_OF_SERVICE"))
                .andExpect(jsonPath("$[0].documentVersion").value(version));
    }

    @Test
    @DisplayName("TC-04: người học khác biết mã tài khoản gửi yêu cầu đổi đồng ý thì nhận không tìm thấy và bị ghi nhật ký")
    void anotherLearnerCannotChangeConsents() throws Exception {
        Learner lan = learner();
        Learner minh = learner();

        mvc.perform(put("/api/v1/accounts/" + lan.id() + "/consents/PRODUCT_NEWS_EMAIL").with(csrf())
                        .cookie(minh.cookies()).contentType(MediaType.APPLICATION_JSON).content("{\"granted\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/accounts/" + lan.id() + "/consents/history").cookie(minh.cookies()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/accounts/" + UUID.randomUUID() + "/privacy").cookie(minh.cookies()))
                .andExpect(status().isNotFound());

        privacyOf(lan).andExpect(jsonPath("$.purposes[1].granted").value(false));
        List<Map<String, Object>> denials = jdbc.queryForList("""
                select actor_id, object_type from audit_log
                where action = 'LEARNING_ACCESS_DENIED' and object_id = ?""", UUID.fromString(lan.id()));
        assertThat(denials).hasSize(2);
        assertThat(denials).allSatisfy(row -> {
            assertThat(row.get("actor_id")).isEqualTo(UUID.fromString(minh.id()));
            assertThat(row.get("object_type")).isEqualTo("USER_ACCOUNT");
        });
    }

    @Test
    @DisplayName("TC-05: mỗi lần đồng ý hay rút lại được ghi nhật ký kèm người thực hiện, nội dung và thời điểm")
    void consentChangesAreAudited() throws Exception {
        Learner lan = learner();
        setConsent(lan, "ANONYMOUS_USAGE_ANALYTICS", true);
        setConsent(lan, "ANONYMOUS_USAGE_ANALYTICS", false);

        List<Map<String, Object>> rows = jdbc.queryForList("""
                select actor_id, action, details::text as details, occurred_at from audit_log
                where actor_id = ? and action in ('CONSENT_GRANTED', 'CONSENT_WITHDRAWN') order by occurred_at""",
                UUID.fromString(lan.id()));
        assertThat(rows).extracting(row -> row.get("action"))
                .containsExactly("CONSENT_GRANTED", "CONSENT_GRANTED", "CONSENT_GRANTED", "CONSENT_WITHDRAWN");
        assertThat(rows.get(3).get("details").toString())
                .contains("ANONYMOUS_USAGE_ANALYTICS").contains("2026-10-01");
        assertThat(rows).allSatisfy(row -> assertThat(row.get("occurred_at")).isNotNull());
    }

    // ----- hỗ trợ -----

    private record Learner(String id, String email, List<Cookie> jar) {

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

    private Learner learner() throws Exception {
        String email = "dong.y." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Lan Anh","email":"%s","password":"%s","acceptTerms":true,
                                 "termsVersion":"2026-10-01","privacyVersion":"2026-10-01"}"""
                                .formatted(email, PASSWORD)))
                .andExpect(status().isAccepted());
        Matcher matcher = Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)")
                .matcher(String.join(" ", awaitMail(email, "VERIFY_EMAIL", 1).links()));
        assertThat(matcher.find()).isTrue();
        List<Cookie> jar = new ArrayList<>();
        absorb(jar, mvc.perform(post("/api/v1/auth/verify-email").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + matcher.group(1) + "\"}"))
                .andExpect(status().isOk()).andReturn());
        return withId(email, jar);
    }

    private Learner login(String email) throws Exception {
        List<Cookie> jar = new ArrayList<>();
        absorb(jar, mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"rememberDevice\":false}"
                                .formatted(email, PASSWORD)))
                .andExpect(status().isOk()).andReturn());
        return withId(email, jar);
    }

    private Learner withId(String email, List<Cookie> jar) throws Exception {
        String session = mvc.perform(get("/api/v1/auth/session").cookie(jar.toArray(Cookie[]::new)))
                .andReturn().getResponse().getContentAsString();
        return new Learner(JSON.readTree(session).get("account").get("id").asString(), email, jar);
    }

    private ResultActions privacyOf(Learner learner) throws Exception {
        return mvc.perform(get("/api/v1/accounts/" + learner.id() + "/privacy").cookie(learner.cookies()));
    }

    private ResultActions history(Learner learner) throws Exception {
        return mvc.perform(get("/api/v1/accounts/" + learner.id() + "/consents/history").cookie(learner.cookies()));
    }

    private ResultActions setConsent(Learner learner, String purpose, boolean granted) throws Exception {
        return mvc.perform(put("/api/v1/accounts/" + learner.id() + "/consents/" + purpose).with(csrf())
                .cookie(learner.cookies()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"granted\":" + granted + "}"));
    }

    private ResultActions accept(Learner learner, String terms, String privacy) throws Exception {
        return mvc.perform(post("/api/v1/accounts/" + learner.id() + "/terms/accept").with(csrf())
                .cookie(learner.cookies()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"termsVersion\":\"%s\",\"privacyVersion\":\"%s\"}".formatted(terms, privacy)));
    }

    private ResultActions event(Learner learner, String route) throws Exception {
        return mvc.perform(post("/api/v1/analytics/events").with(csrf()).cookie(learner.cookies())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"page_view\",\"route\":\"" + route + "\"}"));
    }

    private void publish(String type, String version, List<String> changes) throws Exception {
        mvc.perform(post("/api/v1/dev/legal/versions").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("documentType", type, "version", version,
                                "summary", "Bản cập nhật thử nghiệm.", "changes", changes,
                                "effectiveInSeconds", FAR_FUTURE.toSeconds()))))
                .andExpect(status().isOk());
    }

    private void sendNews(String headline) throws Exception {
        mvc.perform(post("/api/v1/dev/product-news").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\":\"" + headline + "\",\"body\":\"Thử ngay hôm nay.\"}"))
                .andExpect(status().isOk());
    }

    private String sample(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/dev/library/sample").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asString();
    }

    private long usageEvents() {
        return jdbc.queryForObject("select count(*) from usage_events", Long.class);
    }

    private List<MockMailGateway.CapturedMail> mails(String to, String template) {
        return mailbox.messages().stream()
                .filter(mail -> mail.to().equalsIgnoreCase(to) && mail.template().equals(template))
                .toList();
    }

    private MockMailGateway.CapturedMail awaitMail(String to, String template, int count) {
        return await().atMost(Duration.ofSeconds(60))
                .until(() -> Optional.of(mails(to, template)).filter(list -> list.size() >= count),
                        Optional::isPresent)
                .orElseThrow().get(0);
    }
}
