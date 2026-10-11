package com.learningcompanion.auth;

import static org.assertj.core.api.Assertions.assertThat;
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

/** Kiểm thử NCL-01-CN-003 qua API với PostgreSQL thật, bám theo năm tiêu chí chấp nhận của story. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class PasswordIT {

    private static final String OLD_PASSWORD = "song-xanh-mua-thu-2026";
    private static final String NEW_PASSWORD = "bien-xanh-gio-dong-2027";

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
    @DisplayName("TC-01: quên mật khẩu, mở thư, đặt mật khẩu mới thì mọi phiên bị đăng xuất và có thư xác nhận")
    void forgotPasswordFlowReplacesPasswordAndRevokesSessions() throws Exception {
        Browser phone = new Browser();
        String email = verifiedAccount(phone);
        Browser laptop = new Browser();
        login(laptop, email, OLD_PASSWORD).andExpect(status().isOk());
        mailbox.clear();

        forgot("{\"email\":\"" + email.toUpperCase() + "\"}")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.resendAvailableInSeconds").value(60));
        MockMailGateway.CapturedMail mail = awaitMail(email, "PASSWORD_RESET");
        assertThat(mail.html()).contains("30").contains("Đặt mật khẩu mới");
        String token = token(mail);

        check(token).andExpect(status().isNoContent());
        reset(token, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        session(phone).andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.reason").value("SESSION_REVOKED"));
        session(laptop).andExpect(jsonPath("$.authenticated").value(false));
        MockMailGateway.CapturedMail confirmation = awaitMail(email, "PASSWORD_CHANGED");
        assertThat(confirmation.html()).contains("đặt lại qua thư điện tử").contains("/forgot-password");

        login(new Browser(), email, OLD_PASSWORD).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        login(new Browser(), email, NEW_PASSWORD).andExpect(status().isOk());
        reset(token, "mot-mat-khau-khac-nua-2027")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("Yêu cầu đặt lại cho địa chỉ chưa đăng ký nhận phản hồi giống hệt và không có thư")
    void forgotDoesNotRevealUnknownEmails() throws Exception {
        String email = verifiedAccount(new Browser());
        mailbox.clear();

        String known = forgot("{\"email\":\"" + email + "\"}").andReturn().getResponse().getContentAsString();
        String unknown = forgot("{\"email\":\"khong.co." + UUID.randomUUID() + "@example.com\"}")
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        assertThat(unknown).isEqualTo(known);
        awaitMail(email, "PASSWORD_RESET");
        assertThat(mailbox.messages()).hasSize(1);
    }

    @Test
    @DisplayName("TC-02: đường dẫn quá ba mươi phút báo hết hạn và yêu cầu được đường dẫn mới")
    void expiredLinkCanBeReplaced() throws Exception {
        String email = verifiedAccount(new Browser());
        forgot("{\"email\":\"" + email + "\"}");
        String expired = token(awaitMail(email, "PASSWORD_RESET"));

        clock.advance(Duration.ofMinutes(29));
        check(expired).andExpect(status().isNoContent());
        clock.advance(Duration.ofMinutes(1).plusSeconds(1));
        check(expired).andExpect(status().isGone()).andExpect(jsonPath("$.code").value("RESET_TOKEN_EXPIRED"));
        reset(expired, NEW_PASSWORD).andExpect(status().isGone());

        mailbox.clear();
        forgot("{\"token\":\"" + expired + "\"}").andExpect(status().isAccepted());
        String fresh = token(awaitMail(email, "PASSWORD_RESET"));
        assertThat(fresh).isNotEqualTo(expired);
        reset(fresh, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Yêu cầu lại trong vòng một phút không gửi thêm thư; thư mới làm đường dẫn cũ mất hiệu lực")
    void resendCooldownAndInvalidation() throws Exception {
        String email = verifiedAccount(new Browser());
        forgot("{\"email\":\"" + email + "\"}");
        String first = token(awaitMail(email, "PASSWORD_RESET"));
        mailbox.clear();

        forgot("{\"email\":\"" + email + "\"}").andExpect(status().isAccepted());
        Thread.sleep(300);
        assertThat(mailbox.messages()).isEmpty();

        clock.advance(Duration.ofSeconds(61));
        forgot("{\"email\":\"" + email + "\"}");
        String second = token(awaitMail(email, "PASSWORD_RESET"));
        check(first).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
        check(second).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Mật khẩu mới phải theo quy tắc như lúc đăng ký và khác mật khẩu cũ")
    void newPasswordFollowsPolicy() throws Exception {
        String email = verifiedAccount(new Browser());
        forgot("{\"email\":\"" + email + "\"}");
        String token = token(awaitMail(email, "PASSWORD_RESET"));

        reset(token, "Password1234").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_BREACHED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
        reset(token, "ngan").andExpect(jsonPath("$.code").value("PASSWORD_TOO_SHORT"));
        reset(token, OLD_PASSWORD).andExpect(jsonPath("$.code").value("PASSWORD_REUSED"));
        // Các lần bị từ chối không làm hỏng đường dẫn.
        reset(token, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Đặt lại mật khẩu mở luôn khóa đăng nhập do nhập sai trước đó")
    void resetUnlocksLogin() throws Exception {
        String email = verifiedAccount(new Browser());
        for (int i = 0; i < 5; i++) {
            login(new Browser(), email, "sai-mat-khau-" + i);
        }
        login(new Browser(), email, OLD_PASSWORD).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

        forgot("{\"email\":\"" + email + "\"}");
        reset(token(awaitMail(email, "PASSWORD_RESET")), NEW_PASSWORD).andExpect(status().isOk());

        login(new Browser(), email, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-03: nhập sai mật khẩu hiện tại khi đổi mật khẩu bị từ chối và tính một lần nhập sai")
    void wrongCurrentPasswordCountsAsFailedAttempt() throws Exception {
        Browser phone = new Browser();
        String email = verifiedAccount(phone);

        change(phone, "sai-mat-khau", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("currentPassword"))
                .andExpect(jsonPath("$.details.remainingAttempts").value(4));
        // Lần sai này cộng dồn với đăng nhập sai: thêm bốn lần đăng nhập sai thì khóa.
        for (int i = 0; i < 4; i++) {
            login(new Browser(), email, "sai-" + i);
        }
        change(phone, OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        session(phone).andExpect(jsonPath("$.authenticated").value(true));
    }

    @Test
    @DisplayName("Đổi mật khẩu thành công thì đăng xuất mọi thiết bị khác, giữ thiết bị đang dùng")
    void changePasswordRevokesOtherSessions() throws Exception {
        Browser phone = new Browser();
        String email = verifiedAccount(phone);
        Browser laptop = new Browser();
        login(laptop, email, OLD_PASSWORD).andExpect(status().isOk());
        mailbox.clear();

        change(phone, OLD_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedSessions").value(1));

        session(phone).andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.account.passwordChangedAt").exists());
        session(laptop).andExpect(jsonPath("$.reason").value("SESSION_REVOKED"));
        assertThat(awaitMail(email, "PASSWORD_CHANGED").html()).contains("đổi trong mục bảo mật tài khoản");
        change(phone, NEW_PASSWORD, NEW_PASSWORD).andExpect(jsonPath("$.code").value("PASSWORD_REUSED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));
    }

    @Test
    @DisplayName("TC-04: đăng nhập trên ba thiết bị, đăng xuất khỏi mọi thiết bị khác thì hai phiên bị thu hồi")
    void signOutEverywhereElse() throws Exception {
        Browser phone = new Browser();
        String email = verifiedAccount(phone);
        Browser laptop = new Browser();
        Browser tablet = new Browser();
        login(laptop, email, OLD_PASSWORD);
        login(tablet, email, OLD_PASSWORD);

        mvc.perform(post("/api/v1/auth/sessions/revoke-others").with(csrf()).cookie(phone.cookies()))
                .andExpect(jsonPath("$.revokedCount").value(2));

        session(phone).andExpect(jsonPath("$.authenticated").value(true));
        session(laptop).andExpect(jsonPath("$.reason").value("SESSION_REVOKED"));
        session(tablet).andExpect(jsonPath("$.reason").value("SESSION_REVOKED"));
    }

    @Test
    @DisplayName("Đổi mật khẩu cần đăng nhập")
    void changeRequiresSession() throws Exception {
        mvc.perform(post("/api/v1/auth/password/change").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"a\",\"newPassword\":\"b\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-05: yêu cầu, đặt lại và đổi mật khẩu được ghi nhật ký, không chứa mật khẩu hay đường dẫn")
    void passwordOperationsAreAudited() throws Exception {
        Browser phone = new Browser();
        String email = verifiedAccount(phone);
        change(phone, OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isOk());
        clock.advance(Duration.ofSeconds(61));
        forgot("{\"email\":\"" + email + "\"}");
        reset(token(awaitMail(email, "PASSWORD_RESET")), "troi-trong-xanh-2028").andExpect(status().isOk());
        UUID userId = jdbc.queryForObject("select id from user_accounts where email = ?", UUID.class, email);

        List<String> actions = jdbc.queryForList(
                "select action from audit_log where object_id = ? order by occurred_at", String.class, userId);
        assertThat(actions).contains("PASSWORD_CHANGED", "PASSWORD_RESET_REQUESTED", "PASSWORD_RESET_COMPLETED");
        List<String> details = jdbc.queryForList(
                "select details::text from audit_log where object_id = ?", String.class, userId);
        assertThat(details).allSatisfy(text -> assertThat(text)
                .doesNotContain(OLD_PASSWORD).doesNotContain(NEW_PASSWORD).doesNotContain("token="));
    }

    // ----- hỗ trợ -----

    /** Một trình duyệt giữ cookie giữa các yêu cầu. */
    private static final class Browser {
        private final List<Cookie> jar = new ArrayList<>();

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

    private String verifiedAccount(Browser browser) throws Exception {
        String email = "mat.khau." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Lan Anh","email":"%s","password":"%s","acceptTerms":true,
                                 "termsVersion":"2026-10-01","privacyVersion":"2026-10-01"}"""
                                .formatted(email, OLD_PASSWORD)))
                .andExpect(status().isAccepted());
        MockMailGateway.CapturedMail mail = awaitMail(email, "VERIFY_EMAIL");
        Matcher matcher = Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)")
                .matcher(String.join(" ", mail.links()));
        assertThat(matcher.find()).isTrue();
        MvcResult result = mvc.perform(post("/api/v1/auth/verify-email").with(csrf()).cookie(browser.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + matcher.group(1) + "\"}"))
                .andExpect(status().isOk()).andReturn();
        browser.absorb(result);
        return email;
    }

    private ResultActions login(Browser browser, String email, String password) throws Exception {
        ResultActions actions = mvc.perform(post("/api/v1/auth/login").with(csrf()).cookie(browser.cookies())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\",\"rememberDevice\":false}".formatted(email, password)));
        browser.absorb(actions.andReturn());
        return actions;
    }

    private ResultActions forgot(String body) throws Exception {
        return mvc.perform(post("/api/v1/auth/password/forgot").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions check(String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/password/reset/check").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"));
    }

    private ResultActions reset(String token, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/password/reset").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, password)));
    }

    private ResultActions change(Browser browser, String current, String next) throws Exception {
        return mvc.perform(post("/api/v1/auth/password/change").with(csrf()).cookie(browser.cookies())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next)));
    }

    private ResultActions session(Browser browser) throws Exception {
        return mvc.perform(get("/api/v1/auth/session").cookie(browser.cookies()));
    }

    private MockMailGateway.CapturedMail awaitMail(String to, String template) {
        return await().atMost(Duration.ofSeconds(60)).until(() -> mailbox.messages().stream()
                .filter(mail -> mail.to().equalsIgnoreCase(to) && mail.template().equals(template))
                .findFirst(), Optional::isPresent).orElseThrow();
    }

    private static String token(MockMailGateway.CapturedMail mail) {
        Matcher matcher = Pattern.compile("reset-password\\?token=([A-Za-z0-9_-]+)")
                .matcher(String.join(" ", mail.links()));
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
