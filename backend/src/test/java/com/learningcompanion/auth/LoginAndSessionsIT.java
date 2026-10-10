package com.learningcompanion.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.learningcompanion.shared.mail.MockMailGateway;
import com.learningcompanion.shared.time.AdjustableClock;
import com.learningcompanion.support.PostgresTestConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import org.springframework.test.web.servlet.ResultActions;

/** Kiểm thử NCL-01-CN-002 qua API với PostgreSQL thật, bám theo năm tiêu chí chấp nhận của story. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class LoginAndSessionsIT {

    private static final String PASSWORD = "song-xanh-mua-thu-2026";
    private static final String CHROME_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36";
    private static final String CHROME_ANDROID =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 Chrome/140.0 Mobile Safari/537.36";

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
    @DisplayName("TC-01: đăng nhập trên điện thoại có ghi nhớ thiết bị thì phiên hiệu lực ba mươi ngày")
    void rememberedLoginLastsThirtyDays() throws Exception {
        Device phone = Device.fresh(CHROME_ANDROID);
        String email = verifiedAccount(phone);

        MvcResult result = login(phone, email, PASSWORD, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andReturn();

        String sessionCookie = setCookie(result, "lc_session");
        assertThat(sessionCookie).contains("Max-Age=2592000").contains("HttpOnly").contains("SameSite=Lax");
        OffsetDateTime expiresAt = jdbc.queryForObject("""
                select expires_at from user_sessions s join user_accounts u on u.id = s.user_id
                where u.email = ? and s.remember_device order by s.created_at desc limit 1""",
                OffsetDateTime.class, email);
        assertThat(Duration.between(clock.instant(), expiresAt.toInstant()))
                .isBetween(Duration.ofDays(30).minusMinutes(1), Duration.ofDays(30));

        clock.advance(Duration.ofDays(29));
        session(phone).andExpect(jsonPath("$.authenticated").value(true));
        clock.advance(Duration.ofDays(1).plusMinutes(1));
        session(phone)
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.reason").value("SESSION_EXPIRED"));
    }

    @Test
    @DisplayName("TC-02: sai mật khẩu năm lần liên tiếp thì lần thứ sáu bị tạm khóa mười lăm phút kèm thời gian chờ")
    void fiveWrongPasswordsLockLoginForFifteenMinutes() throws Exception {
        Device laptop = Device.fresh(CHROME_WINDOWS);
        String email = verifiedAccount(laptop);
        mailbox.clear();

        login(laptop, email, "sai-lan-1", false)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.details.remainingAttempts").value(4));
        for (int attempt = 2; attempt <= 4; attempt++) {
            login(laptop, email, "sai-lan-" + attempt, false).andExpect(status().isUnauthorized());
        }
        login(laptop, email, "sai-lan-5", false)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.details.retryAfterSeconds").value(900));

        clock.advance(Duration.ofMinutes(5));
        login(laptop, email, PASSWORD, false)
                .andExpect(status().isLocked())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "600"))
                .andExpect(jsonPath("$.details.retryAfterSeconds").value(600));
        awaitMail(email, "ACCOUNT_LOCKED");

        clock.advance(Duration.ofMinutes(10));
        login(laptop, email, PASSWORD, false).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Địa chỉ chưa đăng ký nhận cùng phản hồi và cùng cơ chế khóa như tài khoản thật")
    void unknownEmailBehavesLikeWrongPassword() throws Exception {
        Device laptop = Device.fresh(CHROME_WINDOWS);
        String email = verifiedAccount(laptop);
        String unknown = "khong.ton.tai." + UUID.randomUUID().toString().substring(0, 6) + "@example.com";

        String real = body(login(laptop, email, "sai-mat-khau", false).andReturn());
        String fake = body(login(laptop, unknown, "sai-mat-khau", false).andReturn());
        assertThat(strip(fake)).isEqualTo(strip(real));

        for (int attempt = 2; attempt <= 4; attempt++) {
            login(laptop, unknown, "sai", false);
        }
        login(laptop, unknown, "sai", false).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    @DisplayName("TC-03: đăng nhập từ thiết bị mới thì nhận thư cảnh báo kèm nút đăng xuất thiết bị đó")
    void newDeviceTriggersAlertWithWorkingRevokeLink() throws Exception {
        Device phone = Device.fresh(CHROME_ANDROID);
        String email = verifiedAccount(phone);
        mailbox.clear();

        // Thiết bị đã quen thì không có thư.
        login(phone, email, PASSWORD, true).andExpect(status().isOk());
        Thread.sleep(300);
        assertThat(mailbox.messages()).isEmpty();

        Device library = Device.fresh(CHROME_WINDOWS);
        login(library, email, PASSWORD, false).andExpect(status().isOk());
        MockMailGateway.CapturedMail alert = awaitMail(email, "NEW_DEVICE_LOGIN");
        assertThat(alert.html()).contains("Chrome trên Windows").contains("Đăng xuất thiết bị đó");
        String revokeToken = token(alert, "/devices/revoke");

        mvc.perform(post("/api/v1/auth/sessions/revoke-link").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + revokeToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.browser").value("Chrome"))
                .andExpect(jsonPath("$.operatingSystem").value("Windows"))
                .andExpect(jsonPath("$.alreadyRevoked").value(false));
        session(library)
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.reason").value("SESSION_REVOKED"));
        session(phone).andExpect(jsonPath("$.authenticated").value(true));

        mvc.perform(post("/api/v1/auth/sessions/revoke-link").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + revokeToken + "\"}"))
                .andExpect(jsonPath("$.alreadyRevoked").value(true));
        mvc.perform(post("/api/v1/auth/sessions/revoke-link").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"bia-dat\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REVOKE_LINK_INVALID"));
    }

    @Test
    @DisplayName("TC-04: đăng xuất máy ở thư viện từ điện thoại thì phiên đó bị thu hồi ngay")
    void revokingAnotherDeviceEndsItsSession() throws Exception {
        Device phone = Device.fresh(CHROME_ANDROID);
        String email = verifiedAccount(phone);
        Device library = Device.fresh(CHROME_WINDOWS);
        login(library, email, PASSWORD, false).andExpect(status().isOk());

        MvcResult list = sessions(phone)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.current == true)].deviceType").value("MOBILE"))
                .andExpect(jsonPath("$[?(@.current == false)].browser").value("Chrome"))
                .andExpect(jsonPath("$[?(@.current == false)].operatingSystem").value("Windows"))
                .andExpect(jsonPath("$[?(@.current == false)].localNetwork").value(true))
                .andReturn();
        String libraryId = otherSessionId(list);

        mvc.perform(delete("/api/v1/auth/sessions/" + libraryId).with(csrf()).cookie(phone.cookies()))
                .andExpect(status().isNoContent());

        sessions(library)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
        sessions(phone).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("Đăng xuất mọi thiết bị khác giữ lại thiết bị đang dùng")
    void revokeOthersKeepsCurrentSession() throws Exception {
        Device phone = Device.fresh(CHROME_ANDROID);
        String email = verifiedAccount(phone);
        Device laptop = Device.fresh(CHROME_WINDOWS);
        Device tablet = Device.fresh("Mozilla/5.0 (iPad; CPU OS 18_0 like Mac OS X) Safari/604.1");
        login(laptop, email, PASSWORD, true);
        login(tablet, email, PASSWORD, false);

        mvc.perform(post("/api/v1/auth/sessions/revoke-others").with(csrf()).cookie(phone.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedCount").value(2));

        session(phone).andExpect(jsonPath("$.authenticated").value(true));
        session(laptop).andExpect(jsonPath("$.reason").value("SESSION_REVOKED"));
        session(tablet).andExpect(jsonPath("$.reason").value("SESSION_REVOKED"));
    }

    @Test
    @DisplayName("Không đăng xuất được thiết bị của người khác")
    void cannotRevokeSomeoneElsesSession() throws Exception {
        Device mine = Device.fresh(CHROME_ANDROID);
        verifiedAccount(mine);
        Device theirs = Device.fresh(CHROME_WINDOWS);
        verifiedAccount(theirs);
        String theirSessionId = JsonIds.first(body(sessions(theirs).andReturn()));

        mvc.perform(delete("/api/v1/auth/sessions/" + theirSessionId).with(csrf()).cookie(mine.cookies()))
                .andExpect(status().isNotFound());
        session(theirs).andExpect(jsonPath("$.authenticated").value(true));
    }

    @Test
    @DisplayName("TC-05: không ghi nhớ thiết bị và để hai giờ không thao tác thì phiên hết hạn")
    void idleSessionExpiresAfterTwoHours() throws Exception {
        Device laptop = Device.fresh(CHROME_WINDOWS);
        String email = verifiedAccount(laptop);
        MvcResult result = login(laptop, email, PASSWORD, false).andExpect(status().isOk()).andReturn();
        assertThat(setCookie(result, "lc_session")).doesNotContain("Max-Age");

        clock.advance(Duration.ofMinutes(119));
        sessions(laptop).andExpect(status().isOk());

        clock.advance(Duration.ofHours(2));
        MvcResult expired = sessions(laptop)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"))
                .andReturn();
        assertThat(setCookie(expired, "lc_session")).contains("Max-Age=0");
    }

    @Test
    @DisplayName("Tài khoản chưa xác thực thư điện tử được nhắc xác thực khi nhập đúng mật khẩu")
    void unverifiedAccountIsAskedToVerify() throws Exception {
        String email = uniqueEmail();
        register(email).andExpect(status().isAccepted());

        login(Device.fresh(CHROME_WINDOWS), email, PASSWORD, false)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    @DisplayName("Đăng nhập, đăng nhập sai và đăng xuất từ xa đều được ghi nhật ký")
    void loginEventsAreAudited() throws Exception {
        Device phone = Device.fresh(CHROME_ANDROID);
        String email = verifiedAccount(phone);
        login(phone, email, "sai-mat-khau", false);
        login(phone, email, PASSWORD, false);
        UUID userId = jdbc.queryForObject("select id from user_accounts where email = ?", UUID.class, email);

        List<String> actions = jdbc.queryForList(
                "select action from audit_log where actor_id = ? or object_id = ? order by occurred_at",
                String.class, userId, userId);
        assertThat(actions).contains("LOGIN_FAILED", "LOGIN_SUCCEEDED", "SESSION_CREATED");
    }

    @Test
    @DisplayName("Tự đăng xuất không bị báo là bị đăng xuất từ thiết bị khác")
    void ownLogoutIsNotReportedAsRevoked() throws Exception {
        Device laptop = Device.fresh(CHROME_WINDOWS);
        verifiedAccount(laptop);

        mvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(laptop.cookies()))
                .andExpect(status().isNoContent());
        session(laptop)
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

    // ----- hỗ trợ -----

    /** Một trình duyệt: giữ cookie phiên và cookie thiết bị giữa các yêu cầu, như trình duyệt thật. */
    private static final class Device {
        private final String userAgent;
        private final List<Cookie> jar = new ArrayList<>();

        private Device(String userAgent) {
            this.userAgent = userAgent;
        }

        static Device fresh(String userAgent) {
            return new Device(userAgent);
        }

        Cookie[] cookies() {
            return jar.isEmpty() ? new Cookie[] {new Cookie("lc_none", "1")} : jar.toArray(Cookie[]::new);
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

    private String verifiedAccount(Device device) throws Exception {
        String email = uniqueEmail();
        register(email).andExpect(status().isAccepted());
        String token = token(awaitMail(email, "VERIFY_EMAIL"), "/verify-email");
        MvcResult result = mvc.perform(post("/api/v1/auth/verify-email").with(csrf())
                        .header(HttpHeaders.USER_AGENT, device.userAgent).cookie(device.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        device.absorb(result);
        return email;
    }

    private ResultActions register(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"displayName":"Lan Anh","email":"%s","password":"%s","acceptTerms":true,
                         "termsVersion":"2026-10-01","privacyVersion":"2026-10-01"}""".formatted(email, PASSWORD)));
    }

    private ResultActions login(Device device, String email, String password, boolean remember) throws Exception {
        ResultActions actions = mvc.perform(post("/api/v1/auth/login").with(csrf())
                .header(HttpHeaders.USER_AGENT, device.userAgent).cookie(device.cookies())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\",\"rememberDevice\":%s}"
                        .formatted(email, password, remember)));
        device.absorb(actions.andReturn());
        return actions;
    }

    private ResultActions session(Device device) throws Exception {
        return mvc.perform(get("/api/v1/auth/session").cookie(device.cookies()));
    }

    private ResultActions sessions(Device device) throws Exception {
        return mvc.perform(get("/api/v1/auth/sessions").header(HttpHeaders.USER_AGENT, device.userAgent)
                .cookie(device.cookies()));
    }

    private MockMailGateway.CapturedMail awaitMail(String to, String template) {
        return await().atMost(Duration.ofSeconds(60)).until(() -> mailbox.messages().stream()
                .filter(mail -> mail.to().equalsIgnoreCase(to) && mail.template().equals(template))
                .findFirst(), Optional::isPresent).orElseThrow();
    }

    private static String token(MockMailGateway.CapturedMail mail, String path) {
        Matcher matcher = Pattern.compile(Pattern.quote(path) + "\\?token=([A-Za-z0-9_-]+)")
                .matcher(String.join(" ", mail.links()));
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static String setCookie(MvcResult result, String name) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(name + "="))
                .findFirst()
                .orElseThrow();
    }

    private static String otherSessionId(MvcResult list) throws Exception {
        Matcher matcher = Pattern.compile("\\{\"id\":\"([0-9a-f-]{36})\"[^}]*\"current\":false")
                .matcher(list.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    /** Bỏ các trường thay đổi theo từng yêu cầu để so sánh hai phản hồi. */
    private static String strip(String json) {
        return json.replaceAll("\"requestId\":\"[^\"]*\"", "").replaceAll("\"timestamp\":\"[^\"]*\"", "");
    }

    private static String uniqueEmail() {
        return "dang.nhap." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private static final class JsonIds {
        static String first(String json) {
            Matcher matcher = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"").matcher(json);
            assertThat(matcher.find()).isTrue();
            return matcher.group(1);
        }
    }
}
