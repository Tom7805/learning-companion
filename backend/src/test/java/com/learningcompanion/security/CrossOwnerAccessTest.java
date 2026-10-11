package com.learningcompanion.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.mail.MockMailGateway;
import com.learningcompanion.shared.time.AdjustableClock;
import com.learningcompanion.support.PostgresTestConfiguration;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

/** NCL-01-CN-004 — cách ly dữ liệu học tập theo chủ sở hữu, kiểm qua API với PostgreSQL thật. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class CrossOwnerAccessTest {

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
    private AppProperties properties;

    @BeforeEach
    void reset() {
        clock.reset();
    }

    @Test
    @DisplayName("TC-01: người học mở bản ghi mẫu của mình và nhận đủ nội dung")
    void ownerReadsOwnRecordWithFullContent() throws Exception {
        Browser lan = new Browser();
        String email = verifiedAccount(lan);
        String id = sample(email, "Ghi chú: Định luật Ohm");

        mvc.perform(get("/api/v1/library/documents").cookie(lan.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].contentText").doesNotExist());
        mvc.perform(get("/api/v1/library/documents/" + id).cookie(lan.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Ghi chú: Định luật Ohm"))
                .andExpect(jsonPath("$.contentText").value(org.hamcrest.Matchers.containsString("I = U / R")))
                .andExpect(jsonPath("$.originalFilename").value("dinh-luat-ohm.txt"));
    }

    @Test
    @DisplayName("TC-04: biết mã bản ghi của người khác vẫn nhận 404 như mã không tồn tại, lần từ chối được ghi nhật ký")
    void anotherLearnersRecordLooksMissing() throws Exception {
        Browser lan = new Browser();
        String lanEmail = verifiedAccount(lan);
        String lansDocument = sample(lanEmail, "Nhật ký riêng của Lan");
        Browser minh = new Browser();
        verifiedAccount(minh);

        String theirs = mvc.perform(get("/api/v1/library/documents/" + lansDocument).cookie(minh.cookies()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
        String missing = mvc.perform(get("/api/v1/library/documents/" + UUID.randomUUID()).cookie(minh.cookies()))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertThat(withoutVolatileFields(theirs)).isEqualTo(withoutVolatileFields(missing));
        assertThat(theirs).doesNotContain("Nhật ký riêng");

        mvc.perform(post("/api/v1/library/documents/" + lansDocument + "/file-link").with(csrf())
                        .cookie(minh.cookies()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/library/documents").cookie(minh.cookies()))
                .andExpect(jsonPath("$.length()").value(0));
        // Mã sai định dạng cũng được trả lời như không tồn tại.
        mvc.perform(get("/api/v1/library/documents/khong-phai-ma").cookie(minh.cookies()))
                .andExpect(status().isNotFound());

        UUID minhId = accountId(minh);
        List<Map<String, Object>> denials = jdbc.queryForList("""
                select actor_id, object_type, details::text as details from audit_log
                where action = 'LEARNING_ACCESS_DENIED' and object_id = ?""", UUID.fromString(lansDocument));
        assertThat(denials).hasSize(2);
        assertThat(denials).allSatisfy(row -> {
            assertThat(row.get("actor_id")).isEqualTo(minhId);
            assertThat(row.get("object_type")).isEqualTo("DOCUMENT");
            assertThat((String) row.get("details")).doesNotContain("Nhật ký riêng").doesNotContain("Ohm");
        });
        // Chủ sở hữu vẫn mở bình thường.
        mvc.perform(get("/api/v1/library/documents/" + lansDocument).cookie(lan.cookies()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Chưa đăng nhập thì không đọc được dữ liệu học tập")
    void anonymousCannotRead() throws Exception {
        mvc.perform(get("/api/v1/library/documents")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-02: đường dẫn ký mở được tệp trong năm phút, sau sáu phút kho tệp từ chối và xin được đường dẫn mới")
    void signedLinkExpiresAfterFiveMinutes() throws Exception {
        Browser lan = new Browser();
        String email = verifiedAccount(lan);
        String id = sample(email, "Tệp mẫu");

        JsonNode link = JSON.readTree(mvc.perform(post("/api/v1/library/documents/" + id + "/file-link")
                        .with(csrf()).cookie(lan.cookies()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String url = link.get("url").asString();
        assertThat(url).startsWith("/api/v1/files/");

        // Đường dẫn ký tự nó là quyền: mở được cả khi không có phiên.
        MvcResult opened = mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.startsWith("sandbox")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.startsWith("inline")))
                .andReturn();
        assertThat(new String(opened.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
                .contains("I = U / R");
        mvc.perform(get(url).param("download", "true"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.startsWith("attachment")));

        clock.advance(Duration.ofMinutes(6));
        mvc.perform(get(url))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("FILE_LINK_EXPIRED"));

        String fresh = JSON.readTree(mvc.perform(post("/api/v1/library/documents/" + id + "/file-link")
                        .with(csrf()).cookie(lan.cookies()))
                .andReturn().getResponse().getContentAsString()).get("url").asString();
        assertThat(fresh).isNotEqualTo(url);
        mvc.perform(get(fresh)).andExpect(status().isOk());

        // Sửa một ký tự là chữ ký sai: trả lời như tệp không tồn tại.
        String forged = fresh.substring(0, fresh.length() - 1) + (fresh.endsWith("A") ? "B" : "A");
        mvc.perform(get(forged)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tệp gốc nằm mã hóa trong kho, trong thư mục riêng của chủ sở hữu")
    void originalFileIsEncryptedAtRest() throws Exception {
        Browser lan = new Browser();
        String email = verifiedAccount(lan);
        String id = sample(email, "Tệp mẫu mã hóa");

        String key = jdbc.queryForObject("select storage_key from documents where id = ?", String.class,
                UUID.fromString(id));
        UUID ownerId = accountId(lan);
        assertThat(key).startsWith(ownerId + "/");
        byte[] onDisk = Files.readAllBytes(Path.of(properties.storage().localRoot()).resolve(key));
        assertThat(new String(onDisk, StandardCharsets.UTF_8)).doesNotContain("Ohm").doesNotContain("U / R");
    }

    @Test
    @DisplayName("TC-03: quản trị viên chỉ nhận số bản ghi và dung lượng, không có trường nội dung dù yêu cầu")
    void adminSeesUsageButNeverContent() throws Exception {
        Browser lan = new Browser();
        String lanEmail = verifiedAccount(lan);
        String document = sample(lanEmail, "Bài tập riêng tư");
        sample(lanEmail, "Ghi chú thứ hai");
        UUID lanId = accountId(lan);
        long expectedBytes = jdbc.queryForObject("select sum(size_bytes) from documents where owner_id = ?",
                Long.class, lanId);

        Browser admin = new Browser();
        String adminEmail = verifiedAccount(admin);
        promoteToAdmin(adminEmail);
        Browser adminSession = new Browser();
        login(adminSession, adminEmail);

        String body = mvc.perform(get("/api/v1/admin/users/" + lanId).cookie(adminSession.cookies())
                        .param("include", "content").param("fields", "contentText,title"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(lanEmail))
                .andExpect(jsonPath("$.learningRecords").value(2))
                .andExpect(jsonPath("$.storageBytesUsed").value(expectedBytes))
                .andReturn().getResponse().getContentAsString();
        JsonNode summary = JSON.readTree(body);
        assertThat(summary.propertyNames()).containsExactlyInAnyOrder("id", "email", "displayName", "role", "status", "createdAt",
                "emailVerifiedAt", "learningRecords", "storageBytesUsed");
        assertThat(body).doesNotContain("Bài tập riêng tư").doesNotContain("Ohm");

        // Quản trị viên cũng không mở được tài liệu của người học qua điểm cuối của người học.
        mvc.perform(get("/api/v1/library/documents/" + document).cookie(adminSession.cookies()))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_log where action = 'ADMIN_ACCOUNT_VIEWED' and object_id = ?""",
                Integer.class, lanId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Người học không gọi được điểm cuối quản trị")
    void learnerCannotUseAdminEndpoints() throws Exception {
        Browser lan = new Browser();
        verifiedAccount(lan);
        mvc.perform(get("/api/v1/admin/users/" + accountId(lan)).cookie(lan.cookies()))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("email"))));
    }

    // ----- hỗ trợ -----

    private static String withoutVolatileFields(String json) throws Exception {
        JsonNode node = JSON.readTree(json);
        ((tools.jackson.databind.node.ObjectNode) node).remove(List.of("requestId", "timestamp"));
        return node.toString();
    }

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
        String email = "cach.ly." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
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
        browser.absorb(mvc.perform(post("/api/v1/auth/verify-email").with(csrf()).cookie(browser.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + matcher.group(1) + "\"}"))
                .andExpect(status().isOk()).andReturn());
        return email;
    }

    private void login(Browser browser, String email) throws Exception {
        browser.absorb(mvc.perform(post("/api/v1/auth/login").with(csrf()).cookie(browser.cookies())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"rememberDevice\":false}"
                                .formatted(email, PASSWORD)))
                .andExpect(status().isOk()).andReturn());
    }

    private String sample(String email, String title) throws Exception {
        ResultActions created = mvc.perform(post("/api/v1/dev/library/sample").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"title\":\"%s\"}".formatted(email, title)));
        return JSON.readTree(created.andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("id").asString();
    }

    private void promoteToAdmin(String email) throws Exception {
        mvc.perform(post("/api/v1/dev/accounts/role").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"role\":\"ADMIN\"}".formatted(email)))
                .andExpect(status().isNoContent());
    }

    private UUID accountId(Browser browser) throws Exception {
        String session = mvc.perform(get("/api/v1/auth/session").cookie(browser.cookies()))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JSON.readTree(session).get("account").get("id").asString());
    }
}
