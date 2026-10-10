package com.learningcompanion.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.learningcompanion.shared.mail.MockMailGateway;
import com.learningcompanion.shared.time.AdjustableClock;
import com.learningcompanion.support.PostgresTestConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

/** Kiểm thử NCL-01-CN-001 qua API với PostgreSQL thật, bám theo năm tiêu chí chấp nhận của story. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class AuthControllerIT {

    private static final Pattern TOKEN = Pattern.compile("/verify-email\\?token=([A-Za-z0-9_-]+)");
    private static final String STRONG_PASSWORD = "song-xanh-mua-thu-2026";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MockMailGateway mailbox;

    @Autowired
    private AdjustableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        mailbox.clear();
        clock.reset();
    }

    @Test
    @DisplayName("TC-01: đăng ký hợp lệ tạo tài khoản chờ xác thực và gửi thư trong vòng một phút")
    void registerCreatesPendingAccountAndSendsVerificationMail() throws Exception {
        String email = uniqueEmail();

        register(email, STRONG_PASSWORD)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.resendAvailableInSeconds").value(60));

        Map<String, Object> account = jdbc.queryForMap(
                "select status, role, display_name, password_hash from user_accounts where email = ?", email);
        assertThat(account.get("status")).isEqualTo("PENDING_VERIFICATION");
        assertThat(account.get("role")).isEqualTo("LEARNER");
        assertThat(account.get("display_name")).isEqualTo("Lan Anh");
        assertThat((String) account.get("password_hash")).doesNotContain(STRONG_PASSWORD);

        List<Map<String, Object>> consents = jdbc.queryForList("""
                select c.purpose, c.document_version from consent_history c
                join user_accounts u on u.id = c.user_id where u.email = ? order by c.purpose""", email);
        assertThat(consents).extracting(row -> row.get("purpose"))
                .containsExactly("PRIVACY_POLICY", "TERMS_OF_SERVICE");
        assertThat(consents).extracting(row -> row.get("document_version")).containsOnly("2026-10-01");

        MockMailGateway.CapturedMail mail = awaitMail(email, "VERIFY_EMAIL");
        assertThat(mail.links()).anySatisfy(link ->
                assertThat(link).startsWith("http://localhost:5173/verify-email?token="));
        assertThat(mail.html()).contains("Lan Anh").contains("24");
    }

    @Test
    @DisplayName("TC-02: mật khẩu đã lộ bị từ chối kèm lời giải thích, không tạo tài khoản")
    void breachedPasswordIsRejected() throws Exception {
        String email = uniqueEmail();

        register(email, "Password1234")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_BREACHED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value(
                        org.hamcrest.Matchers.containsString("cụm từ dài hơn")));

        assertThat(countAccounts(email)).isZero();
    }

    @Test
    @DisplayName("Mật khẩu ngắn hơn mười ký tự bị từ chối")
    void shortPasswordIsRejected() throws Exception {
        register(uniqueEmail(), "k7#vQ2!mZ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_SHORT"));
    }

    @Test
    @DisplayName("TC-03: địa chỉ đã có tài khoản nhận phản hồi giống hệt, chủ địa chỉ nhận thư báo")
    void duplicateEmailGetsIdenticalResponseAndOwnerIsNotified() throws Exception {
        String email = uniqueEmail();
        verifyAccount(email);
        mailbox.clear();

        String fresh = register(uniqueEmail(), STRONG_PASSWORD).andReturn().getResponse().getContentAsString();
        String duplicate = register(email.toUpperCase(), "mot-mat-khau-hoan-toan-khac")
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(duplicate.replace(email, "X")).isEqualTo(fresh.replaceAll("hoc\\.[^\"]+@example\\.com", "X"));
        assertThat(countAccounts(email)).isEqualTo(1);
        MockMailGateway.CapturedMail notice = awaitMail(email, "REGISTRATION_ATTEMPT");
        assertThat(notice.links()).contains("http://localhost:5173/login");
    }

    @Test
    @DisplayName("Gửi lại thư trả cùng phản hồi dù địa chỉ đã đăng ký hay chưa")
    void resendDoesNotRevealWhetherEmailExists() throws Exception {
        String registered = uniqueEmail();
        register(registered, STRONG_PASSWORD);
        clock.advance(Duration.ofSeconds(61));

        String known = resend(Map.of("email", registered)).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String unknown = resend(Map.of("email", uniqueEmail())).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(known).isEqualTo(unknown);
    }

    @Test
    @DisplayName("TC-04: đường dẫn quá 24 giờ báo hết hạn và gửi lại được thư mới")
    void expiredLinkCanBeReplacedByResend() throws Exception {
        String email = uniqueEmail();
        register(email, STRONG_PASSWORD);
        String token = tokenFrom(awaitMail(email, "VERIFY_EMAIL"));

        clock.advance(Duration.ofHours(24).plusSeconds(1));
        verify(token)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("VERIFICATION_TOKEN_EXPIRED"));
        assertThat(accountStatus(email)).isEqualTo("PENDING_VERIFICATION");

        mailbox.clear();
        resend(Map.of("token", token)).andExpect(status().isAccepted());
        String fresh = tokenFrom(awaitMail(email, "VERIFY_EMAIL"));
        assertThat(fresh).isNotEqualTo(token);

        verify(fresh).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Đường dẫn còn hạn ở mốc 23 giờ 59 phút")
    void linkIsStillValidJustBeforeExpiry() throws Exception {
        String email = uniqueEmail();
        register(email, STRONG_PASSWORD);
        String token = tokenFrom(awaitMail(email, "VERIFY_EMAIL"));

        clock.advance(Duration.ofHours(24).minusMinutes(1));

        verify(token).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Xác thực thành công kích hoạt tài khoản và đăng nhập luôn bằng cookie HttpOnly")
    void verifyActivatesAccountAndStartsSession() throws Exception {
        String email = uniqueEmail();
        register(email, STRONG_PASSWORD);
        String token = tokenFrom(awaitMail(email, "VERIFY_EMAIL"));

        MvcResult result = verify(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.emailVerifiedAt").exists())
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith("lc_session=").contains("HttpOnly").contains("SameSite=Lax")
                .contains("Path=/").doesNotContain("Max-Age");
        Cookie session = new Cookie("lc_session", setCookie.substring("lc_session=".length(), setCookie.indexOf(';')));

        mvc.perform(get("/api/v1/auth/session").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.account.displayName").value("Lan Anh"));

        mvc.perform(post("/api/v1/auth/logout").cookie(session).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/session").cookie(session))
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    @DisplayName("Đường dẫn chỉ dùng được một lần")
    void linkIsSingleUse() throws Exception {
        String email = uniqueEmail();
        register(email, STRONG_PASSWORD);
        String token = tokenFrom(awaitMail(email, "VERIFY_EMAIL"));

        verify(token).andExpect(status().isOk());
        verify(token).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERIFICATION_TOKEN_USED"));
    }

    @Test
    @DisplayName("Gửi lại chỉ có tác dụng sau một phút chờ và làm đường dẫn cũ mất hiệu lực")
    void resendRespectsCooldownAndInvalidatesOldLink() throws Exception {
        String email = uniqueEmail();
        register(email, STRONG_PASSWORD);
        String first = tokenFrom(awaitMail(email, "VERIFY_EMAIL"));
        mailbox.clear();

        resend(Map.of("email", email)).andExpect(status().isAccepted());
        Thread.sleep(300);
        assertThat(mailbox.messages()).isEmpty();

        clock.advance(Duration.ofSeconds(61));
        resend(Map.of("email", email)).andExpect(status().isAccepted());
        String second = tokenFrom(awaitMail(email, "VERIFY_EMAIL"));

        verify(first).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_TOKEN_INVALID"));
        verify(second).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Đường dẫn bịa đặt bị từ chối")
    void unknownTokenIsRejected() throws Exception {
        verify("khong-ton-tai-" + UUID.randomUUID())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("TC-05: đăng ký và xác thực được ghi nhật ký với người thực hiện, nội dung, thời điểm")
    void registrationAndVerificationAreAudited() throws Exception {
        String email = uniqueEmail();
        verifyAccount(email);
        UUID userId = jdbc.queryForObject("select id from user_accounts where email = ?", UUID.class, email);

        List<Map<String, Object>> rows = jdbc.queryForList("""
                select action, actor_type, actor_id, occurred_at, details::text as details, request_id
                from audit_log where actor_id = ? or object_id = ? order by occurred_at, action""", userId, userId);

        assertThat(rows).extracting(row -> row.get("action")).contains("ACCOUNT_REGISTERED", "CONSENT_GRANTED",
                "VERIFICATION_EMAIL_SENT", "EMAIL_VERIFIED", "SESSION_CREATED");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("occurred_at")).isNotNull();
            assertThat(row.get("request_id")).isNotNull();
            assertThat((String) row.get("details")).doesNotContain(STRONG_PASSWORD).doesNotContain("token=");
        });
        assertThat(rows).filteredOn(row -> "EMAIL_VERIFIED".equals(row.get("action")))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.get("actor_type")).isEqualTo("USER");
                    assertThat(row.get("actor_id")).isEqualTo(userId);
                });
    }

    @Test
    @DisplayName("Nhật ký thao tác không sửa hay xóa được")
    void auditLogIsAppendOnly() throws Exception {
        verifyAccount(uniqueEmail());

        assertThatThrownBy(() -> jdbc.update("update audit_log set action = 'X'"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from audit_log"))
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("Yêu cầu thiếu mã CSRF bị chặn")
    void registerRequiresCsrfToken() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueEmail(), STRONG_PASSWORD, true)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Thiếu đồng ý điều khoản hoặc địa chỉ thư sai định dạng bị từ chối theo từng trường")
    void invalidFormIsRejectedPerField() throws Exception {
        mvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueEmail(), STRONG_PASSWORD, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TERMS_NOT_ACCEPTED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("acceptTerms"));

        mvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("khong-phai-email", STRONG_PASSWORD, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    @Test
    @DisplayName("Trang điều khoản trả phiên bản đang áp dụng")
    void legalVersionsArePublic() throws Exception {
        mvc.perform(get("/api/v1/legal/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terms.version").value("2026-10-01"))
                .andExpect(jsonPath("$.privacy.version").value("2026-10-01"));
    }

    private void verifyAccount(String email) throws Exception {
        register(email, STRONG_PASSWORD).andExpect(status().isAccepted());
        verify(tokenFrom(awaitMail(email, "VERIFY_EMAIL"))).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions register(String email, String password)
            throws Exception {
        return mvc.perform(post("/api/v1/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(email, password, true)));
    }

    private org.springframework.test.web.servlet.ResultActions verify(String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/verify-email").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions resend(Map<String, String> fields) throws Exception {
        Map.Entry<String, String> field = fields.entrySet().iterator().next();
        return mvc.perform(post("/api/v1/auth/verify-email/resend").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"" + field.getKey() + "\":\"" + field.getValue() + "\"}"));
    }

    private static String body(String email, String password, boolean acceptTerms) {
        return """
                {"displayName":"  Lan   Anh ","email":"%s","password":"%s","acceptTerms":%s,
                 "termsVersion":"2026-10-01","privacyVersion":"2026-10-01"}"""
                .formatted(email, password, acceptTerms);
    }

    private MockMailGateway.CapturedMail awaitMail(String to, String template) {
        // Tiêu chí: thư phải tới trong vòng một phút; thực tế chỉ vài trăm mili giây.
        return await().atMost(Duration.ofSeconds(60)).until(() -> mailbox.messages().stream()
                .filter(mail -> mail.to().equalsIgnoreCase(to) && mail.template().equals(template))
                .findFirst(), java.util.Optional::isPresent).orElseThrow();
    }

    private static String tokenFrom(MockMailGateway.CapturedMail mail) {
        Matcher matcher = TOKEN.matcher(String.join(" ", mail.links()));
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private int countAccounts(String email) {
        return jdbc.queryForObject("select count(*) from user_accounts where email = lower(?)", Integer.class, email);
    }

    private String accountStatus(String email) {
        return jdbc.queryForObject("select status from user_accounts where email = ?", String.class, email);
    }

    private static String uniqueEmail() {
        return "hoc." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }
}
