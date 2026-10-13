package com.learningcompanion.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.learningcompanion.privacy.application.AccountPurgeJob;
import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.mail.MockMailGateway;
import com.learningcompanion.shared.time.AdjustableClock;
import com.learningcompanion.support.PostgresTestConfiguration;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** NCL-01-CN-009 — xóa tài khoản có thời gian chờ, kiểm qua API với PostgreSQL thật. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class AccountDeletionIT {

    private static final String PASSWORD = "song-xanh-mua-thu-2026";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MockMailGateway mailbox;

    @Autowired
    private AdjustableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AccountPurgeJob purgeJob;

    @Autowired
    private AppProperties properties;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    @DisplayName("TC-01: nhập đúng mật khẩu thì tài khoản chờ xóa, mọi phiên bị thu hồi và thư nêu hạn chót đổi ý")
    void requestStartsGracePeriod() throws Exception {
        Learner phone = learner();
        Learner laptop = login(phone.email());
        Instant before = clock.instant();

        JsonNode body = read(deletion(phone, PASSWORD).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.pending").value(true))
                .andExpect(jsonPath("$.graceDays").value(30)));
        Instant scheduledFor = Instant.parse(body.get("scheduledFor").asString());
        assertThat(Duration.between(before, scheduledFor)).isBetween(Duration.ofDays(30), Duration.ofDays(30).plusMinutes(1));

        for (Learner device : List.of(phone, laptop)) {
            mvc.perform(get("/api/v1/auth/session").cookie(device.cookies()))
                    .andExpect(jsonPath("$.authenticated").value(false))
                    .andExpect(jsonPath("$.reason").value("SESSION_REVOKED"));
        }
        assertThat(statusOf(phone)).isEqualTo("PENDING_DELETION");
        MockMailGateway.CapturedMail mail = awaitMail(phone.email(), "ACCOUNT_DELETION_SCHEDULED");
        assertThat(mail.subject()).isEqualTo("Tài khoản của bạn sẽ bị xóa sau 30 ngày");
        assertThat(mail.html()).contains("xóa vĩnh viễn vào").contains("Hủy yêu cầu xóa");
        assertThat(String.join(" ", mail.links())).contains("/login");
    }

    @Test
    @DisplayName("Đang chờ xóa: đăng nhập được để quyết định, mọi chức năng khác bị chặn và không nhận thông báo khác")
    void pendingAccountIsLockedDownAndSilent() throws Exception {
        Learner lan = learner();
        sample(lan.email());
        setConsent(lan, "PRODUCT_NEWS_EMAIL", true);
        deletion(lan, PASSWORD).andExpect(status().isAccepted());
        awaitMail(lan.email(), "ACCOUNT_DELETION_SCHEDULED");
        mailbox.clear();

        Learner again = login(lan.email());
        mvc.perform(get("/api/v1/auth/session").cookie(again.cookies()))
                .andExpect(jsonPath("$.account.status").value("PENDING_DELETION"))
                .andExpect(jsonPath("$.account.deletionScheduledFor").exists());
        mvc.perform(get("/api/v1/library/documents").cookie(again.cookies())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_PENDING_DELETION"));
        mvc.perform(post("/api/v1/exports").with(csrf()).cookie(again.cookies())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/accounts/" + lan.id() + "/deletion").cookie(again.cookies()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pending").value(true));

        mvc.perform(post("/api/v1/dev/product-news").contentType(MediaType.APPLICATION_JSON)
                .content("{\"headline\":\"Sổ tay mới\",\"body\":\"Thử ngay.\"}")).andExpect(status().isOk());
        // Đăng nhập từ trình duyệt mới cũng không gửi cảnh báo thiết bị mới.
        login(lan.email());
        Thread.sleep(500);
        assertThat(mailbox.messages()).noneMatch(mail -> mail.to().equalsIgnoreCase(lan.email()));

        // Quên mật khẩu vẫn đặt lại được để vào hủy yêu cầu xóa.
        mvc.perform(post("/api/v1/auth/password/forgot").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + lan.email() + "\"}")).andExpect(status().isAccepted());
        awaitMail(lan.email(), "PASSWORD_RESET");
    }

    @Test
    @DisplayName("TC-02: chờ xóa được mười ngày, đăng nhập lại và hủy thì tài khoản hoạt động lại với nguyên dữ liệu")
    void cancelRestoresAccount() throws Exception {
        Learner lan = learner();
        String document = sample(lan.email());
        deletion(lan, PASSWORD).andExpect(status().isAccepted());

        clock.advance(Duration.ofDays(10));
        Learner back = login(lan.email());
        mvc.perform(delete("/api/v1/accounts/" + lan.id() + "/deletion").with(csrf()).cookie(back.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(false))
                .andExpect(jsonPath("$.scheduledFor").doesNotExist());

        assertThat(statusOf(lan)).isEqualTo("ACTIVE");
        mvc.perform(get("/api/v1/auth/session").cookie(back.cookies()))
                .andExpect(jsonPath("$.account.status").value("ACTIVE"))
                .andExpect(jsonPath("$.account.deletionScheduledFor").doesNotExist());
        mvc.perform(get("/api/v1/library/documents/" + document).cookie(back.cookies())).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Ghi chú: Định luật Ohm"));
        assertThat(awaitMail(lan.email(), "ACCOUNT_DELETION_CANCELLED").html()).contains("đã được hủy");

        // Đã hủy thì tác vụ xóa không đụng tới, kể cả khi qua hạn cũ.
        clock.advance(Duration.ofDays(25));
        purgeJob.run();
        assertThat(statusOf(lan)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("TC-03: quá ba mươi ngày thì tác vụ hằng ngày xóa vĩnh viễn mọi dữ liệu, nhật ký chỉ còn mã ẩn danh")
    void purgeErasesEverything() throws Exception {
        Learner lan = learner();
        String document = sample(lan.email());
        setConsent(lan, "ANONYMOUS_USAGE_ANALYTICS", true);
        String storageKey = jdbc.queryForObject("select storage_key from documents where id = ?::uuid", String.class,
                document);
        Path file = Path.of(properties.storage().localRoot()).toAbsolutePath().normalize().resolve(storageKey);
        assertThat(file).exists();
        deletion(lan, PASSWORD).andExpect(status().isAccepted());
        assertThat(auditRows("actor_id", lan.id())).isPositive();

        clock.advance(Duration.ofDays(29));
        purgeJob.run();
        assertThat(statusOf(lan)).isEqualTo("PENDING_DELETION");

        clock.advance(Duration.ofDays(1).plusMinutes(1));
        assertThat(purgeJob.run()).isGreaterThanOrEqualTo(1);

        assertThat(count("select count(*) from user_accounts where id = ?::uuid", lan.id())).isZero();
        assertThat(count("select count(*) from documents where owner_id = ?::uuid", lan.id())).isZero();
        assertThat(count("select count(*) from consents where user_id = ?::uuid", lan.id())).isZero();
        assertThat(count("select count(*) from consent_history where user_id = ?::uuid", lan.id())).isZero();
        assertThat(count("select count(*) from user_sessions where user_id = ?::uuid", lan.id())).isZero();
        assertThat(count("select count(*) from account_deletion_requests where user_id = ?::uuid", lan.id())).isZero();
        assertThat(count("select count(*) from login_throttles where email = ?", lan.email())).isZero();
        await().atMost(Duration.ofSeconds(10)).until(() -> !Files.exists(file));

        // Nhật ký: không còn mã tài khoản cũ, các dòng của người học nay mang một mã ẩn danh và không còn IP.
        assertThat(auditRows("actor_id", lan.id())).isZero();
        assertThat(auditRows("object_id", lan.id())).isZero();
        Map<String, Object> completed = jdbc.queryForMap("""
                select object_id::text as anon, actor_type, actor_id, details::text as details from audit_log
                where action = 'ACCOUNT_DELETION_COMPLETED' order by occurred_at desc limit 1""");
        String anonymousId = completed.get("anon").toString();
        assertThat(completed.get("actor_type")).isEqualTo("SYSTEM");
        assertThat(completed.get("actor_id")).isNull();
        assertThat(completed.get("details").toString()).contains("\"library\": 1").doesNotContain(lan.email());
        assertThat(count("""
                select count(*) from audit_log where actor_id = ?::uuid and action = 'ACCOUNT_DELETION_REQUESTED'""",
                anonymousId)).isEqualTo(1);
        assertThat(count("select count(*) from audit_log where actor_id = ?::uuid and ip_address is not null",
                anonymousId)).isZero();
        Map<String, Object> tombstone = jdbc.queryForMap(
                "select erased::text as erased from deleted_account_tombstones where id = ?::uuid", anonymousId);
        assertThat(tombstone.get("erased").toString()).contains("library").contains("profile");

        MockMailGateway.CapturedMail goodbye = awaitMail(lan.email(), "ACCOUNT_DELETED");
        assertThat(goodbye.html()).contains("đã bị xóa vĩnh viễn").contains("mã đã ẩn danh");
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"rememberDevice\":false}"
                                .formatted(lan.email(), PASSWORD)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        // Địa chỉ thư được giải phóng: đăng ký lại được từ đầu.
        register(lan.email()).andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("Nhật ký vẫn chỉ ghi thêm: không ai sửa hay xóa được dòng nhật ký ngoài đường ẩn danh có kiểm soát")
    void auditLogStaysAppendOnly() throws Exception {
        Learner lan = learner();
        assertThatThrownBy(() -> jdbc.update("update audit_log set actor_id = null where actor_id = ?::uuid", lan.id()))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from audit_log where actor_id = ?::uuid", lan.id()))
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("TC-04: phiên bị đánh cắp không có mật khẩu thì bị từ chối và chủ tài khoản nhận thư cảnh báo")
    void stolenSessionCannotDelete() throws Exception {
        Learner lan = learner();
        Learner thief = login(lan.email());
        mailbox.clear();

        deletion(thief, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REAUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.details.hasPassword").value(true));
        deletion(thief, "doan-mo-mat-khau").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));

        assertThat(statusOf(lan)).isEqualTo("ACTIVE");
        mvc.perform(get("/api/v1/auth/session").cookie(thief.cookies()))
                .andExpect(jsonPath("$.authenticated").value(true));
        MockMailGateway.CapturedMail warning = awaitMail(lan.email(), "ACCOUNT_DELETION_ATTEMPT");
        assertThat(warning.html()).contains("bị từ chối").contains("/devices");
        // Tối đa mỗi giờ một thư cảnh báo, để không ai dùng chức năng này quấy rối chủ tài khoản.
        Thread.sleep(300);
        assertThat(mails(lan.email(), "ACCOUNT_DELETION_ATTEMPT")).hasSize(1);
        assertThat(count("""
                select count(*) from audit_log where action = 'ACCOUNT_DELETION_REJECTED' and object_id = ?::uuid""",
                lan.id())).isEqualTo(2);
    }

    @Test
    @DisplayName("Người học khác biết mã tài khoản cũng không yêu cầu hay hủy xóa được; lần từ chối được ghi nhật ký")
    void otherLearnerCannotTouchDeletion() throws Exception {
        Learner lan = learner();
        Learner minh = learner();

        mvc.perform(post("/api/v1/accounts/" + lan.id() + "/deletion").with(csrf()).cookie(minh.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/accounts/" + lan.id() + "/deletion").with(csrf()).cookie(minh.cookies()))
                .andExpect(status().isNotFound());
        assertThat(statusOf(lan)).isEqualTo("ACTIVE");
        assertThat(count("""
                select count(*) from audit_log where action = 'LEARNING_ACCESS_DENIED' and actor_id = ?::uuid
                and object_id = ?::uuid""", minh.id(), lan.id())).isEqualTo(2);
    }

    @Test
    @DisplayName("Tài khoản chỉ đăng nhập bằng nhà cung cấp: vừa đăng nhập lại thì xác nhận được, quá năm phút thì không")
    void providerOnlyAccountConfirmsWithFreshSignIn() throws Exception {
        Learner stale = learner();
        jdbc.update("update user_accounts set password_hash = null where id = ?::uuid", stale.id());
        clock.advance(Duration.ofMinutes(6));
        deletion(stale, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.details.hasPassword").value(false));
        assertThat(statusOf(stale)).isEqualTo("ACTIVE");

        Learner fresh = learner();
        jdbc.update("update user_accounts set password_hash = null where id = ?::uuid", fresh.id());
        deletion(fresh, null).andExpect(status().isAccepted()).andExpect(jsonPath("$.pending").value(true));
    }

    @Test
    @DisplayName("TC-05: nhật ký ghi người thực hiện, nội dung và thời điểm của yêu cầu, hủy và hoàn tất xóa")
    void auditRecordsEveryStep() throws Exception {
        Learner lan = learner();
        deletion(lan, PASSWORD).andExpect(status().isAccepted());
        Learner back = login(lan.email());
        mvc.perform(delete("/api/v1/accounts/" + lan.id() + "/deletion").with(csrf()).cookie(back.cookies()))
                .andExpect(status().isOk());

        for (String action : List.of("ACCOUNT_DELETION_REQUESTED", "ACCOUNT_DELETION_CANCELLED")) {
            Map<String, Object> row = jdbc.queryForMap("""
                    select actor_type, actor_id::text as actor, occurred_at, details::text as details from audit_log
                    where action = ? and object_id = ?::uuid""", action, lan.id());
            assertThat(row.get("actor_type")).isEqualTo("USER");
            assertThat(row.get("actor")).isEqualTo(lan.id());
            assertThat(row.get("occurred_at")).isNotNull();
            assertThat(row.get("details").toString()).contains("scheduledFor");
        }
        assertThat(jdbc.queryForObject("""
                select details->>'verifiedBy' from audit_log
                where action = 'ACCOUNT_DELETION_REQUESTED' and object_id = ?::uuid""", String.class, lan.id()))
                .isEqualTo("PASSWORD");
    }

    // ---------------------------------------------------------------- hỗ trợ

    private ResultActions deletion(Learner learner, String password) throws Exception {
        String body = password == null ? "{}" : "{\"password\":\"" + password + "\"}";
        return mvc.perform(post("/api/v1/accounts/" + learner.id() + "/deletion").with(csrf())
                .cookie(learner.cookies()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions setConsent(Learner learner, String purpose, boolean granted) throws Exception {
        return mvc.perform(put("/api/v1/accounts/" + learner.id() + "/consents/" + purpose).with(csrf())
                .cookie(learner.cookies()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"granted\":" + granted + "}")).andExpect(status().isOk());
    }

    private String sample(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/dev/library/sample").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asString();
    }

    private String statusOf(Learner learner) {
        return jdbc.queryForObject("select status from user_accounts where id = ?::uuid", String.class, learner.id());
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private long auditRows(String column, String id) {
        return count("select count(*) from audit_log where " + column + " = ?::uuid", id);
    }

    private static JsonNode read(ResultActions actions) throws Exception {
        return JSON.readTree(actions.andReturn().getResponse().getContentAsString());
    }

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

    private ResultActions register(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"displayName":"Lan Anh","email":"%s","password":"%s","acceptTerms":true,
                         "termsVersion":"2026-10-01","privacyVersion":"2026-10-01"}""".formatted(email, PASSWORD)));
    }

    private Learner learner() throws Exception {
        String email = "xoa.tai.khoan." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        register(email).andExpect(status().isAccepted());
        Matcher matcher = Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)")
                .matcher(String.join(" ", awaitMail(email, "VERIFY_EMAIL").links()));
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

    private List<MockMailGateway.CapturedMail> mails(String to, String template) {
        return mailbox.messages().stream()
                .filter(mail -> mail.to().equalsIgnoreCase(to) && mail.template().equals(template))
                .toList();
    }

    private MockMailGateway.CapturedMail awaitMail(String to, String template) {
        return await().atMost(Duration.ofSeconds(60))
                .until(() -> Optional.of(mails(to, template)).filter(list -> !list.isEmpty()), Optional::isPresent)
                .orElseThrow().get(0);
    }
}
