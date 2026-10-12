package com.learningcompanion.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.learningcompanion.shared.mail.MockMailGateway;
import com.learningcompanion.shared.time.AdjustableClock;
import com.learningcompanion.support.PostgresTestConfiguration;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
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

/**
 * NCL-01-CN-007 — đăng nhập bằng tài khoản của nhà cung cấp định danh. Chạy trọn luồng chuyển hướng qua
 * nhà cung cấp giả lập: bắt đầu, trang cho phép quyền, quay về kèm mã ủy quyền và mã chống giả mạo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class OAuthIT {

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

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    @DisplayName("Chỉ xin quyền đọc tên và địa chỉ thư, kèm mã chống giả mạo và PKCE")
    void authorizationRequestAsksForMinimalScopes() throws Exception {
        mvc.perform(get("/api/v1/auth/oauth/providers"))
                .andExpect(jsonPath("$[0].id").value("google"))
                .andExpect(jsonPath("$[0].name").value("Google"));
        Browser browser = new Browser();
        Map<String, String> query = start(browser, "LOGIN");

        assertThat(query).containsEntry("scope", "openid email profile")
                .containsEntry("code_challenge_method", "S256")
                .containsEntry("response_type", "code")
                .containsKeys("state", "code_challenge");
        assertThat(query.get("redirect_uri")).endsWith("/auth/callback/google");
        assertThat(browser.cookie("lc_oauth")).isNotNull();
    }

    @Test
    @DisplayName("TC-01: người mới cho phép quyền và đồng ý điều khoản thì có tài khoản với thư đã xác thực và vào thẳng ứng dụng")
    void newUserSignsUpWithVerifiedEmail() throws Exception {
        Browser browser = new Browser();
        String email = "moi." + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com";

        JsonNode result = json(completeAuthorization(browser, "LOGIN", email, "Nguyễn Minh An")
                .andExpect(status().isOk()).andReturn());
        assertThat(result.get("status").asString()).isEqualTo("TERMS_REQUIRED");
        assertThat(result.get("email").asString()).isEqualTo(email);
        assertThat(result.get("displayName").asString()).isEqualTo("Nguyễn Minh An");
        assertThat(accountCount(email)).isZero();

        // Chưa đồng ý điều khoản thì không tạo tài khoản.
        signup(browser, result.get("pendingToken").asString(), "Minh An", false)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TERMS_NOT_ACCEPTED"));
        signup(browser, result.get("pendingToken").asString(), "Minh An", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNED_IN"))
                .andExpect(jsonPath("$.account.email").value(email))
                .andExpect(jsonPath("$.account.status").value("ACTIVE"))
                .andExpect(jsonPath("$.account.emailVerifiedAt").exists())
                .andExpect(jsonPath("$.account.hasPassword").value(false));

        mvc.perform(get("/api/v1/auth/session").cookie(browser.cookies()))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.account.displayName").value("Minh An"));
        assertThat(jdbc.queryForList("""
                select purpose from consents c join user_accounts u on u.id = c.user_id where u.email = ?""",
                String.class, email)).contains("TERMS_OF_SERVICE", "PRIVACY_POLICY");
        // Mã tiếp tục chỉ dùng được một lần.
        signup(browser, result.get("pendingToken").asString(), "Minh An", true)
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("OAUTH_PENDING_INVALID"));

        // Lần sau đăng nhập lại bằng Google thì vào thẳng.
        Browser later = new Browser();
        completeAuthorization(later, "LOGIN", email, "Nguyễn Minh An")
                .andExpect(jsonPath("$.status").value("SIGNED_IN"));
    }

    @Test
    @DisplayName("TC-02: địa chỉ thư đã có tài khoản dùng mật khẩu thì phải nhập mật khẩu một lần để liên kết, không tự gộp")
    void existingPasswordAccountRequiresPasswordToLink() throws Exception {
        String email = passwordAccount();
        Browser browser = new Browser();

        JsonNode result = json(completeAuthorization(browser, "LOGIN", email, "Lan Anh").andReturn());
        assertThat(result.get("status").asString()).isEqualTo("LINK_REQUIRED");
        assertThat(result.path("account").isMissingNode() || result.path("account").isNull()).isTrue();
        mvc.perform(get("/api/v1/auth/session").cookie(browser.cookies()))
                .andExpect(jsonPath("$.authenticated").value(false));
        assertThat(identityCount(email)).isZero();

        String token = result.get("pendingToken").asString();
        link(browser, token, "sai-mat-khau").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.details.remainingAttempts").value(4));
        assertThat(identityCount(email)).isZero();

        link(browser, token, PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNED_IN"))
                .andExpect(jsonPath("$.account.hasPassword").value(true));
        assertThat(identityCount(email)).isEqualTo(1);
        assertThat(accountCount(email)).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-03: mã chống giả mạo không khớp, đã dùng, hết hạn hoặc từ trình duyệt khác đều bị từ chối và ghi nhật ký")
    void tamperedStateIsRejectedAndAudited() throws Exception {
        long before = rejections();
        Browser browser = new Browser();
        Map<String, String> query = start(browser, "LOGIN");
        Map<String, String> back = authorize(query, "gia.mao@gmail.com", "Gia Mạo", true);

        callback(browser, back.get("code"), back.get("state") + "x", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OAUTH_STATE_INVALID"));
        callback(new Browser(), back.get("code"), back.get("state"), null)
                .andExpect(jsonPath("$.code").value("OAUTH_STATE_INVALID"));

        Browser expired = new Browser();
        Map<String, String> late = authorize(start(expired, "LOGIN"), "tre@gmail.com", "Trễ", true);
        clock.advance(Duration.ofMinutes(11));
        callback(expired, late.get("code"), late.get("state"), null)
                .andExpect(jsonPath("$.code").value("OAUTH_STATE_INVALID"));

        assertThat(rejections()).isEqualTo(before + 3);
        assertThat(jdbc.queryForList("""
                select details ->> 'reason' from audit_log where action = 'OAUTH_LOGIN_REJECTED'
                order by occurred_at desc limit 3""", String.class))
                .containsExactlyInAnyOrder("UNKNOWN_STATE", "BROWSER_MISMATCH", "EXPIRED");
        assertThat(accountCount("gia.mao@gmail.com")).isZero();
    }

    @Test
    @DisplayName("Mã chống giả mạo chỉ dùng được một lần; hủy ở nhà cung cấp hay thư chưa xác thực đều không đăng nhập")
    void stateIsSingleUseAndCancellationIsHandled() throws Exception {
        Browser browser = new Browser();
        Map<String, String> back = authorize(start(browser, "LOGIN"), "mot.lan@gmail.com", "Một Lần", true);
        callback(browser, back.get("code"), back.get("state"), null).andExpect(status().isOk());
        callback(browser, back.get("code"), back.get("state"), null)
                .andExpect(jsonPath("$.code").value("OAUTH_STATE_INVALID"));

        Browser cancelled = new Browser();
        Map<String, String> query = start(cancelled, "LOGIN");
        callback(cancelled, null, query.get("state"), "access_denied")
                .andExpect(jsonPath("$.code").value("OAUTH_CANCELLED"));

        Browser unverified = new Browser();
        Map<String, String> notVerified = authorize(start(unverified, "LOGIN"), "chua@gmail.com", "Chưa", false);
        callback(unverified, notVerified.get("code"), notVerified.get("state"), null)
                .andExpect(jsonPath("$.code").value("OAUTH_EMAIL_UNVERIFIED"));
    }

    @Test
    @DisplayName("TC-04: tài khoản chỉ đăng nhập qua Google và chưa có mật khẩu thì phải đặt mật khẩu trước khi gỡ liên kết")
    void unlinkRequiresPassword() throws Exception {
        Browser browser = new Browser();
        String email = "chi.google." + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com";
        JsonNode pending = json(completeAuthorization(browser, "LOGIN", email, "Chỉ Google").andReturn());
        String accountId = json(signup(browser, pending.get("pendingToken").asString(), "Chỉ Google", true)
                .andReturn()).get("account").get("id").asString();

        JsonNode identities = json(mvc.perform(get("/api/v1/accounts/" + accountId + "/linked-identities")
                .cookie(browser.cookies())).andReturn());
        assertThat(identities.size()).isEqualTo(1);
        assertThat(identities.get(0).get("provider").asString()).isEqualTo("google");
        String identityId = identities.get(0).get("id").asString();

        unlink(browser, accountId, identityId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PASSWORD_REQUIRED_TO_UNLINK"));
        // Chưa có mật khẩu thì đăng nhập bằng mật khẩu không được, dù gõ gì.
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"bat-ky-mat-khau\",\"rememberDevice\":false}"
                                .formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mvc.perform(post("/api/v1/auth/password/set").with(csrf()).cookie(browser.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"ngan\"}"))
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_SHORT"));
        mvc.perform(post("/api/v1/auth/password/set").with(csrf()).cookie(browser.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/password/set").with(csrf()).cookie(browser.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PASSWORD_ALREADY_SET"));

        unlink(browser, accountId, identityId).andExpect(status().isNoContent());
        assertThat(identityCount(email)).isZero();
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"rememberDevice\":false}".formatted(email, PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Liên kết từ phần cài đặt khi đang đăng nhập; tài khoản Google đã gắn với người khác thì bị từ chối")
    void linkFromSettings() throws Exception {
        String email = passwordAccount();
        Browser browser = login(email);
        String accountId = accountId(browser);

        completeAuthorization(browser, "LINK", "khac.dia.chi." + UUID.randomUUID().toString().substring(0, 6)
                + "@gmail.com", "Lan Anh").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("LINKED"));
        assertThat(identityCount(email)).isEqualTo(1);

        // Người khác thử liên kết đúng tài khoản Google đó.
        String otherEmail = passwordAccount();
        Browser other = login(otherEmail);
        String googleEmail = jdbc.queryForObject("select email from external_identities where user_id = ?",
                String.class, UUID.fromString(accountId));
        completeAuthorization(other, "LINK", googleEmail, "Ai Đó")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXTERNAL_IDENTITY_IN_USE"));

        // Bắt đầu liên kết mà chưa đăng nhập thì không được.
        mvc.perform(post("/api/v1/auth/oauth/google/start").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"LINK\"}")).andExpect(status().isUnauthorized());
        // Người khác không xem được danh sách liên kết của tài khoản này.
        mvc.perform(get("/api/v1/accounts/" + accountId + "/linked-identities").cookie(other.cookies()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-05: liên kết và gỡ liên kết được ghi nhật ký kèm người thực hiện, nhà cung cấp và thời điểm")
    void linkingIsAudited() throws Exception {
        String email = passwordAccount();
        Browser browser = new Browser();
        JsonNode pending = json(completeAuthorization(browser, "LOGIN", email, "Lan Anh").andReturn());
        String accountId = json(link(browser, pending.get("pendingToken").asString(), PASSWORD).andReturn())
                .get("account").get("id").asString();
        String identityId = jdbc.queryForObject("select id::text from external_identities where user_id = ?",
                String.class, UUID.fromString(accountId));
        unlink(browser, accountId, identityId).andExpect(status().isNoContent());

        List<Map<String, Object>> rows = jdbc.queryForList("""
                select action, actor_id, details::text as details, occurred_at from audit_log
                where actor_id = ? and action in ('EXTERNAL_IDENTITY_LINKED', 'EXTERNAL_IDENTITY_UNLINKED')
                order by occurred_at""", UUID.fromString(accountId));
        assertThat(rows).extracting(row -> row.get("action"))
                .containsExactly("EXTERNAL_IDENTITY_LINKED", "EXTERNAL_IDENTITY_UNLINKED");
        assertThat(rows.get(0).get("details").toString()).contains("GOOGLE").contains("PASSWORD_CONFIRMED");
        assertThat(rows).allSatisfy(row -> assertThat(row.get("occurred_at")).isNotNull());
    }

    // ----- hỗ trợ -----

    private static final class Browser {
        private final List<Cookie> jar = new ArrayList<>();

        Cookie[] cookies() {
            return jar.isEmpty() ? new Cookie[] {new Cookie("lc_none", "1")} : jar.toArray(Cookie[]::new);
        }

        Cookie cookie(String name) {
            return jar.stream().filter(cookie -> cookie.getName().equals(name)).findFirst().orElse(null);
        }

        void absorb(MvcResult result) {
            for (String header : result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)) {
                String name = header.substring(0, header.indexOf('='));
                String value = header.substring(name.length() + 1, header.indexOf(';'));
                jar.removeIf(cookie -> cookie.getName().equals(name));
                if (!value.isEmpty() && !header.contains("Max-Age=0")) {
                    jar.add(new Cookie(name, value));
                }
            }
        }
    }

    private Map<String, String> start(Browser browser, String mode) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/oauth/google/start").with(csrf()).cookie(browser.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"" + mode + "\"}"))
                .andExpect(status().isOk()).andReturn();
        browser.absorb(result);
        return query(json(result).get("authorizationUrl").asString());
    }

    private Map<String, String> authorize(Map<String, String> query, String email, String name, boolean verified)
            throws Exception {
        String body = JSON.writeValueAsString(Map.of("redirectUri", query.get("redirect_uri"),
                "state", query.get("state"), "codeChallenge", query.get("code_challenge"), "email", email,
                "name", name, "emailVerified", verified, "allow", true));
        MvcResult result = mvc.perform(post("/api/v1/dev/oauth/authorize").contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isOk()).andReturn();
        return query(json(result).get("redirectUrl").asString());
    }

    private ResultActions callback(Browser browser, String code, String state, String error) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("code", code);
        body.put("state", state);
        body.put("error", error);
        ResultActions actions = mvc.perform(post("/api/v1/auth/oauth/google/callback").with(csrf())
                .cookie(browser.cookies()).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body)));
        browser.absorb(actions.andReturn());
        return actions;
    }

    private ResultActions completeAuthorization(Browser browser, String mode, String email, String name)
            throws Exception {
        Map<String, String> back = authorize(start(browser, mode), email, name, true);
        return callback(browser, back.get("code"), back.get("state"), null);
    }

    private ResultActions signup(Browser browser, String token, String name, boolean accept) throws Exception {
        ResultActions actions = mvc.perform(post("/api/v1/auth/oauth/signup").with(csrf()).cookie(browser.cookies())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("token", token, "displayName", name, "acceptTerms", accept,
                        "termsVersion", "2026-10-01", "privacyVersion", "2026-10-01"))));
        browser.absorb(actions.andReturn());
        return actions;
    }

    private ResultActions link(Browser browser, String token, String password) throws Exception {
        ResultActions actions = mvc.perform(post("/api/v1/auth/oauth/link").with(csrf()).cookie(browser.cookies())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("token", token, "password", password))));
        browser.absorb(actions.andReturn());
        return actions;
    }

    private ResultActions unlink(Browser browser, String accountId, String identityId) throws Exception {
        return mvc.perform(delete("/api/v1/accounts/" + accountId + "/linked-identities/" + identityId).with(csrf())
                .cookie(browser.cookies()));
    }

    private String passwordAccount() throws Exception {
        String email = "mat.khau." + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com";
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Lan Anh","email":"%s","password":"%s","acceptTerms":true,
                                 "termsVersion":"2026-10-01","privacyVersion":"2026-10-01"}"""
                                .formatted(email, PASSWORD)))
                .andExpect(status().isAccepted());
        MockMailGateway.CapturedMail mail = await().atMost(Duration.ofSeconds(60)).until(() -> mailbox.messages()
                .stream().filter(m -> m.to().equalsIgnoreCase(email) && m.template().equals("VERIFY_EMAIL"))
                .findFirst(), Optional::isPresent).orElseThrow();
        Matcher matcher = Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)")
                .matcher(String.join(" ", mail.links()));
        assertThat(matcher.find()).isTrue();
        mvc.perform(post("/api/v1/auth/verify-email").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + matcher.group(1) + "\"}")).andExpect(status().isOk());
        return email;
    }

    private Browser login(String email) throws Exception {
        Browser browser = new Browser();
        browser.absorb(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"rememberDevice\":false}"
                                .formatted(email, PASSWORD)))
                .andExpect(status().isOk()).andReturn());
        return browser;
    }

    private String accountId(Browser browser) throws Exception {
        return json(mvc.perform(get("/api/v1/auth/session").cookie(browser.cookies())).andReturn())
                .get("account").get("id").asString();
    }

    private int accountCount(String email) {
        return jdbc.queryForObject("select count(*) from user_accounts where email = ?", Integer.class, email);
    }

    private int identityCount(String email) {
        return jdbc.queryForObject("""
                select count(*) from external_identities i join user_accounts u on u.id = i.user_id
                where u.email = ?""", Integer.class, email);
    }

    private long rejections() {
        return jdbc.queryForObject("select count(*) from audit_log where action = 'OAUTH_LOGIN_REJECTED'", Long.class);
    }

    private static JsonNode json(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static Map<String, String> query(String url) {
        Map<String, String> params = new HashMap<>();
        String raw = URI.create(url).getRawQuery();
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            params.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return params;
    }
}
