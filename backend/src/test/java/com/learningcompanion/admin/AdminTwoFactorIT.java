package com.learningcompanion.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.learningcompanion.admin.application.AdminProvisioningService;
import com.learningcompanion.admin.application.TotpService;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.mail.MockMailGateway;
import com.learningcompanion.shared.time.AdjustableClock;
import com.learningcompanion.support.PostgresTestConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

/** NCL-03-CN-002 — tài khoản quản trị viên duy nhất có xác thực hai bước, kiểm qua API và cơ sở dữ liệu thật. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class AdminTwoFactorIT {

    private static final String PASSWORD = "quan-tri-he-thong-2026";
    private static final String CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private static final Pattern SECRET = Pattern.compile("secret=([A-Z2-7]+)");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AdminProvisioningService provisioning;

    @Autowired
    private TotpService totp;

    @Autowired
    private AdjustableClock clock;

    @Autowired
    private MockMailGateway mailbox;

    private String adminEmail;

    @BeforeEach
    void freshAdmin() {
        clock.reset();
        // Hệ thống chỉ có một quản trị viên: hạ quản trị viên của lớp kiểm thử khác trước khi tạo người mới.
        jdbc.update("update user_accounts set role = 'LEARNER' where role = 'ADMIN'");
        adminEmail = "quan.tri." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        provisioning.createAdmin(adminEmail, "Quản trị viên", PASSWORD);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    @DisplayName("Lần đăng nhập đầu tiên buộc thiết lập ứng dụng sinh mã, nhận mười mã khôi phục, phiên tám giờ (TC-01)")
    void firstLoginForcesEnrollmentThenOpensEightHourAdminSession() throws Exception {
        Browser browser = new Browser();
        browser.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(adminEmail, PASSWORD)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.twoFactorRequired").value(true))
                .andExpect(jsonPath("$.stage").value("ENROLL"));
        // Chưa có phiên: mật khẩu đúng chưa đủ để mở khu quản trị.
        browser.perform(get("/api/v1/admin/me")).andExpect(status().isUnauthorized());
        browser.perform(get("/api/v1/auth/admin/two-factor")).andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("ENROLL"))
                .andExpect(jsonPath("$.email").value(adminEmail));

        byte[] secret = browser.enroll();
        // Mã sai khi thiết lập không mở phiên.
        browser.perform(post("/api/v1/auth/admin/two-factor/enrollment/confirm")
                        .contentType(MediaType.APPLICATION_JSON).content(code(wrongCode(secret))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_CODE_INVALID"));

        Instant before = clock.instant();
        String body = browser.perform(post("/api/v1/auth/admin/two-factor/enrollment/confirm")
                        .contentType(MediaType.APPLICATION_JSON).content(code(currentCode(secret))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signedIn").value(true))
                .andExpect(jsonPath("$.account.role").value("ADMIN"))
                .andExpect(jsonPath("$.recoveryCodes", hasSize(10)))
                .andReturn().getResponse().getContentAsString();
        Instant expiresAt = Instant.parse(JsonPath.read(body, "$.sessionExpiresAt"));
        assertThat(Duration.between(before, expiresAt)).isBetween(Duration.ofHours(8).minusSeconds(5),
                Duration.ofHours(8).plusSeconds(5));
        browser.perform(get("/api/v1/admin/me")).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(adminEmail));

        // Phiên quản trị: tám giờ hạn cứng, nhưng ba mươi phút không thao tác thì hết hạn.
        assertThat(jdbc.queryForObject("""
                select count(*) from user_sessions s join user_accounts a on a.id = s.user_id
                where a.email = ? and s.two_factor_verified and not s.remember_device
                  and s.max_expires_at between s.created_at + interval '7 hours 59 minutes'
                                           and s.created_at + interval '8 hours 1 minute'
                  and s.expires_at <= s.created_at + interval '30 minutes'""", Integer.class, adminEmail))
                .isEqualTo(1);
        assertThat(audited("ADMIN_TWO_FACTOR_ENROLLED")).isEqualTo(1);
        assertThat(audited("LOGIN_SUCCEEDED")).isEqualTo(1);

        // Lần sau: mật khẩu rồi mã sáu chữ số.
        clock.advance(Duration.ofSeconds(31));
        Browser again = new Browser();
        again.login("VERIFY");
        again.perform(post("/api/v1/auth/admin/two-factor/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(code(currentCode(secret))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.signedIn").value(true))
                .andExpect(jsonPath("$.recoveryCodes", hasSize(0)));
        again.perform(get("/api/v1/admin/me")).andExpect(status().isOk());
        // Cookie bước hai đã bị xóa: không dùng lại được để mở phiên thứ hai.
        assertThat(again.has("lc_admin_challenge")).isFalse();

        // Ba mươi phút không thao tác thì phiên hết hạn, dù chưa tới tám giờ.
        clock.advance(Duration.ofMinutes(31));
        again.perform(get("/api/v1/admin/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    @DisplayName("Phiên quản trị hết hạn sau tám giờ kể từ lúc đăng nhập dù vẫn thao tác (TC-01)")
    void adminSessionEndsAfterEightHoursEvenWhenActive() throws Exception {
        Browser browser = new Browser();
        browser.login("ENROLL");
        byte[] secret = browser.enroll();
        browser.confirm(secret);
        for (int i = 0; i < 16; i++) {
            clock.advance(Duration.ofMinutes(29));
            browser.perform(get("/api/v1/admin/me")).andExpect(status().isOk());
        }
        clock.advance(Duration.ofMinutes(29));
        browser.perform(get("/api/v1/admin/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Sai mã năm lần liên tiếp thì khóa đăng nhập quản trị mười lăm phút và gửi thư cảnh báo (TC-02)")
    void fiveWrongCodesLockAdminLoginForFifteenMinutesAndSendMail() throws Exception {
        byte[] secret = enrolledSecret();
        Browser browser = new Browser();
        browser.login("VERIFY");
        for (int attempt = 1; attempt <= 4; attempt++) {
            browser.perform(post("/api/v1/auth/admin/two-factor/verify").contentType(MediaType.APPLICATION_JSON)
                            .content(code(wrongCode(secret))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.remainingAttempts").value(5 - attempt));
        }
        browser.perform(post("/api/v1/auth/admin/two-factor/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(code(wrongCode(secret))))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ADMIN_LOGIN_LOCKED"))
                .andExpect(jsonPath("$.details.retryAfterSeconds").value(900));
        // Khóa cả mã đúng lẫn bước mật khẩu.
        browser.perform(post("/api/v1/auth/admin/two-factor/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(code(currentCode(secret))))
                .andExpect(status().isLocked());
        new Browser().perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(adminEmail, PASSWORD)))
                .andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("ADMIN_LOGIN_LOCKED"));

        MockMailGateway.CapturedMail mail = await().atMost(Duration.ofSeconds(30)).until(() -> mailbox.messages()
                .stream().filter(m -> m.to().equals(adminEmail) && m.template().equals("ADMIN_LOGIN_LOCKED"))
                .findFirst(), java.util.Optional::isPresent).orElseThrow();
        assertThat(mail.subject()).contains("tạm khóa");
        assertThat(audited("ADMIN_TWO_FACTOR_FAILED")).isEqualTo(5);
        assertThat(audited("ADMIN_TWO_FACTOR_LOCKED")).isEqualTo(1);

        clock.advance(Duration.ofMinutes(15));
        Browser later = new Browser();
        later.login("VERIFY");
        later.perform(post("/api/v1/auth/admin/two-factor/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(code(currentCode(secret))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.signedIn").value(true));
    }

    @Test
    @DisplayName("Mã khôi phục chưa dùng cho đăng nhập, bị đánh dấu đã dùng và buộc thiết lập lại (TC-03)")
    void recoveryCodeSignsInOnceAndForcesReEnrollment() throws Exception {
        Browser first = new Browser();
        first.login("ENROLL");
        byte[] oldSecret = first.enroll();
        List<String> codes = first.confirm(oldSecret);

        Browser lostPhone = new Browser();
        lostPhone.login("VERIFY");
        lostPhone.perform(post("/api/v1/auth/admin/two-factor/recover").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryCode\":\"" + codes.get(3).toUpperCase() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signedIn").value(false))
                .andExpect(jsonPath("$.stage").value("ENROLL"));
        assertThat(jdbc.queryForObject("""
                select count(*) from admin_recovery_codes c join user_accounts a on a.id = c.account_id
                where a.email = ? and c.used_at is not null""", Integer.class, adminEmail)).isEqualTo(1);
        lostPhone.perform(get("/api/v1/auth/admin/two-factor")).andExpect(jsonPath("$.stage").value("ENROLL"));

        // Mã đã dùng không dùng lại được.
        Browser replay = new Browser();
        replay.login("VERIFY");
        replay.perform(post("/api/v1/auth/admin/two-factor/recover").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryCode\":\"" + codes.get(3) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECOVERY_CODE_INVALID"));

        clock.advance(Duration.ofSeconds(31));
        byte[] newSecret = lostPhone.enroll();
        assertThat(newSecret).isNotEqualTo(oldSecret);
        List<String> fresh = lostPhone.confirm(newSecret);
        assertThat(fresh).hasSize(10).doesNotContainAnyElementsOf(codes);
        lostPhone.perform(get("/api/v1/admin/me")).andExpect(status().isOk());
        assertThat(audited("ADMIN_RECOVERY_CODE_USED")).isEqualTo(1);

        // Ứng dụng cũ (điện thoại bị mất) không còn đăng nhập được.
        clock.advance(Duration.ofSeconds(31));
        Browser thief = new Browser();
        thief.login("VERIFY");
        thief.perform(post("/api/v1/auth/admin/two-factor/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(code(currentCode(oldSecret))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Bỏ dở sau khi dùng mã khôi phục: lần sau vẫn phải nhập mã rồi thiết lập lại (TC-03)")
    void abandonedRecoveryStillRequiresSecondFactorThenReEnrollment() throws Exception {
        Browser first = new Browser();
        first.login("ENROLL");
        byte[] secret = first.enroll();
        List<String> codes = first.confirm(secret);

        Browser recovering = new Browser();
        recovering.login("VERIFY");
        recovering.perform(post("/api/v1/auth/admin/two-factor/recover").contentType(MediaType.APPLICATION_JSON)
                .content("{\"recoveryCode\":\"" + codes.get(0) + "\"}")).andExpect(status().isOk());

        // Bỏ dở: chỉ mật khẩu không đủ để tự thiết lập ứng dụng sinh mã mới.
        clock.advance(Duration.ofSeconds(31));
        Browser next = new Browser();
        next.login("VERIFY");
        next.perform(post("/api/v1/auth/admin/two-factor/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(code(currentCode(secret))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signedIn").value(false))
                .andExpect(jsonPath("$.stage").value("ENROLL"));
        next.perform(get("/api/v1/admin/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Đã có quản trị viên thì yêu cầu tạo thêm bị từ chối và ghi nhật ký (TC-04)")
    void secondAdminIsRejected() throws Exception {
        long before = audited("ADMIN_CREATION_REJECTED");
        assertThatThrownBy(() -> provisioning.createAdmin("nguoi.thu.hai@example.com", "Người thứ hai", PASSWORD))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(ErrorCode.ADMIN_ALREADY_EXISTS));
        assertThat(audited("ADMIN_CREATION_REJECTED")).isEqualTo(before + 1);
        assertThat(jdbc.queryForObject("select count(*) from user_accounts where role = 'ADMIN'", Integer.class))
                .isEqualTo(1);

        // Cấp vai trò quản trị cho tài khoản khác cũng bị từ chối, kể cả khi ghi thẳng vào cơ sở dữ liệu.
        String learner = "hoc.vien." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        jdbc.update("""
                insert into user_accounts (id, email, display_name, password_hash, role, status, created_at,
                                           updated_at)
                values (?, ?, 'Học viên', 'x', 'LEARNER', 'ACTIVE', now(), now())""", UUID.randomUUID(), learner);
        mvc.perform(post("/api/v1/dev/accounts/role").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"role\":\"ADMIN\"}".formatted(learner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN_ALREADY_EXISTS"));
        assertThatThrownBy(() -> jdbc.update("update user_accounts set role = 'ADMIN' where email = ?", learner))
                .hasMessageContaining("uq_user_accounts_single_admin");
    }

    @Test
    @DisplayName("Người học mở đường dẫn quản trị bị từ chối và ghi nhật ký; quản trị viên không mở chức năng học tập (TC-05)")
    void learnerIsDeniedAdminAreaAndAdminIsDeniedLearningFeatures() throws Exception {
        Browser admin = new Browser();
        admin.login("ENROLL");
        admin.confirm(admin.enroll());
        admin.perform(get("/api/v1/library/documents")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_log l join user_accounts a on a.id = l.actor_id
                where a.email = ? and l.action = 'ROLE_ACCESS_DENIED'""", Integer.class, adminEmail)).isEqualTo(1);

        // Phiên mở bằng mật khẩu trước khi tài khoản thành quản trị viên không mang quyền quản trị.
        jdbc.update("update user_accounts set role = 'LEARNER' where role = 'ADMIN'");
        String email = "hoc.vien." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        jdbc.update("""
                insert into user_accounts (id, email, display_name, password_hash, role, status, email_verified_at,
                                           created_at, updated_at)
                select ?, ?, 'Học viên', password_hash, 'LEARNER', 'ACTIVE', now(), now(), now()
                from user_accounts where email = ?""", UUID.randomUUID(), email, adminEmail);
        Browser learner = new Browser();
        learner.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email, PASSWORD))).andExpect(status().isOk());
        learner.perform(get("/api/v1/admin/me")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_log l join user_accounts a on a.id = l.actor_id
                where a.email = ? and l.action = 'ROLE_ACCESS_DENIED'""", Integer.class, email)).isEqualTo(1);
        jdbc.update("update user_accounts set role = 'ADMIN' where email = ?", email);
        learner.perform(get("/api/v1/admin/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Không có bước hai hợp lệ thì không gọi được các điểm cuối của bước hai")
    void secondStepNeedsAValidChallenge() throws Exception {
        mvc.perform(get("/api/v1/auth/admin/two-factor")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_CHALLENGE_INVALID"));
        mvc.perform(post("/api/v1/auth/admin/two-factor/verify").with(csrf())
                        .cookie(new Cookie("lc_admin_challenge", "gia-mao"))
                        .contentType(MediaType.APPLICATION_JSON).content(code("123456")))
                .andExpect(status().isUnauthorized());
        Browser browser = new Browser();
        browser.login("ENROLL");
        clock.advance(Duration.ofMinutes(11));
        browser.perform(post("/api/v1/auth/admin/two-factor/enrollment")).andExpect(status().isUnauthorized());
    }

    private byte[] enrolledSecret() throws Exception {
        Browser browser = new Browser();
        browser.login("ENROLL");
        byte[] secret = browser.enroll();
        browser.confirm(secret);
        clock.advance(Duration.ofSeconds(31));
        return secret;
    }

    private long audited(String action) {
        return jdbc.queryForObject("""
                select count(*) from audit_log l join user_accounts a on a.id = l.object_id or a.id = l.actor_id
                where a.email = ? and l.action = ?""", Long.class, adminEmail, action);
    }

    private String currentCode(byte[] secret) {
        return totp.codeAt(secret, TotpService.stepAt(clock.instant()));
    }

    private String wrongCode(byte[] secret) {
        String right = currentCode(secret);
        long step = TotpService.stepAt(clock.instant());
        for (int candidate = 0; ; candidate++) {
            String code = String.format("%06d", candidate);
            if (!code.equals(right) && !code.equals(totp.codeAt(secret, step - 1))
                    && !code.equals(totp.codeAt(secret, step + 1))) {
                return code;
            }
        }
    }

    private static String code(String value) {
        return "{\"code\":\"" + value + "\"}";
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\",\"rememberDevice\":true}".formatted(email, password);
    }

    /** Một trình duyệt: giữ cookie giữa các yêu cầu như trình duyệt thật. */
    private final class Browser {

        private final List<Cookie> jar = new ArrayList<>();

        ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
            request.header(HttpHeaders.USER_AGENT, CHROME).with(csrf());
            if (!jar.isEmpty()) {
                request.cookie(jar.toArray(Cookie[]::new));
            }
            ResultActions result = mvc.perform(request);
            absorb(result.andReturn());
            return result;
        }

        boolean has(String name) {
            return jar.stream().anyMatch(cookie -> cookie.getName().equals(name));
        }

        void login(String stage) throws Exception {
            perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(credentials(adminEmail, PASSWORD)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.stage").value(stage));
        }

        byte[] enroll() throws Exception {
            String body = perform(post("/api/v1/auth/admin/two-factor/enrollment")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String uri = JsonPath.read(body, "$.otpauthUri");
            assertThat(uri).startsWith("otpauth://totp/Learning%20Companion:");
            Matcher matcher = SECRET.matcher(uri);
            assertThat(matcher.find()).isTrue();
            assertThat(matcher.group(1)).isEqualTo(JsonPath.read(body, "$.secret"));
            return decodeBase32(matcher.group(1));
        }

        List<String> confirm(byte[] secret) throws Exception {
            String body = perform(post("/api/v1/auth/admin/two-factor/enrollment/confirm")
                    .contentType(MediaType.APPLICATION_JSON).content(code(currentCode(secret))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.signedIn").value(true))
                    .andReturn().getResponse().getContentAsString();
            return JsonPath.read(body, "$.recoveryCodes");
        }

        private void absorb(MvcResult result) {
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

    private static byte[] decodeBase32(String text) {
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
}
