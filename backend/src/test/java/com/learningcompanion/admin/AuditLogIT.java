package com.learningcompanion.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.audit.AuditRetentionJob;
import com.learningcompanion.shared.mail.MockMailGateway;
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
import org.springframework.transaction.support.TransactionTemplate;

/** NCL-03-CN-001 — ghi và tra cứu nhật ký thao tác, kiểm qua API và cơ sở dữ liệu thật. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class AuditLogIT {

    private static final String PASSWORD = "song-xanh-mua-thu-2026";
    private static final String CHROME_ON_WINDOWS = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MockMailGateway mailbox;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuditLogger auditLogger;

    @Autowired
    private AuditRetentionJob retentionJob;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    @DisplayName("TC-01: ba lần sai một lần đúng, lọc theo tài khoản ra đúng bốn dòng đăng nhập kèm thiết bị, thời điểm, kết quả")
    void loginHistoryOfOneAccount() throws Exception {
        User admin = admin();
        User lan = learner();
        for (int i = 0; i < 3; i++) {
            mvc.perform(withBrowser(post("/api/v1/auth/login")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(credentials(lan.email(), "sai-mat-khau-" + i)))
                    .andExpect(status().isUnauthorized());
        }
        login(lan.email());

        search(admin, "account=" + lan.email() + "&category=LOGIN").andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.items[0].action").value("LOGIN_SUCCEEDED"))
                .andExpect(jsonPath("$.items[0].outcome").value("SUCCESS"))
                .andExpect(jsonPath("$.items[0].actor.email").value(lan.email()))
                .andExpect(jsonPath("$.items[1].action").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.items[1].outcome").value("FAILURE"))
                .andExpect(jsonPath("$.items[1].actorType").value("ANONYMOUS"))
                .andExpect(jsonPath("$.items[1].subject.email").value(lan.email()))
                .andExpect(jsonPath("$.items[1].details.reason").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.items[*].category", everyItem(is("LOGIN"))))
                .andExpect(jsonPath("$.items[*].occurredAt", everyItem(notNullValue())))
                .andExpect(jsonPath("$.items[*].device.browser", everyItem(is("Chrome"))))
                .andExpect(jsonPath("$.items[*].device.operatingSystem", everyItem(is("Windows"))))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

        search(admin, "account=" + lan.id() + "&action=LOGIN_FAILED")
                .andExpect(jsonPath("$.items", hasSize(3)));
    }

    @Test
    @DisplayName("TC-02: không ghi được nhật ký thì thao tác bị hủy, dữ liệu giữ nguyên và báo lỗi")
    void failingAuditAbortsTheOperation() throws Exception {
        User lan = learner();
        changePreferences(lan, "{\"themeMode\":\"DARK\"}").andExpect(status().isOk());
        long rowsBefore = auditRows(lan.id());

        jdbc.execute("""
                create or replace function test_fail_audit() returns trigger language plpgsql as $$
                begin
                  if NEW.actor_id = '%s'::uuid then raise exception 'giả lập lỗi lưu trữ nhật ký'; end if;
                  return NEW;
                end $$""".formatted(lan.id()));
        jdbc.execute("create trigger test_fail_audit before insert on audit_log "
                + "for each row execute function test_fail_audit()");
        try {
            changePreferences(lan, "{\"themeMode\":\"LIGHT\"}").andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        } finally {
            jdbc.execute("drop trigger test_fail_audit on audit_log");
            jdbc.execute("drop function test_fail_audit()");
        }

        assertThat(jdbc.queryForObject("select theme_mode from ui_preferences where owner_id = ?::uuid",
                String.class, lan.id())).isEqualTo("DARK");
        assertThat(auditRows(lan.id())).isEqualTo(rowsBefore);
    }

    @Test
    @DisplayName("TC-03: sửa hay xóa một dòng nhật ký bị từ chối, lần cố can thiệp được ghi lại, dòng vẫn còn")
    void tamperingIsRefusedAndRecorded() throws Exception {
        User admin = admin();
        User lan = learner();
        String rowId = jdbc.queryForObject(
                "select id::text from audit_log where actor_id = ?::uuid order by occurred_at desc limit 1",
                String.class, lan.id());

        for (MockHttpServletRequestBuilder attempt : List.of(delete("/api/v1/admin/audit-logs/" + rowId),
                put("/api/v1/admin/audit-logs/" + rowId), patch("/api/v1/admin/audit-logs/" + rowId))) {
            mvc.perform(attempt.with(csrf()).cookie(admin.cookies()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"action\":\"LOGIN_SUCCEEDED\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUDIT_LOG_IMMUTABLE"));
        }

        mvc.perform(delete("/api/v1/admin/audit-logs").with(csrf()).cookie(admin.cookies()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUDIT_LOG_IMMUTABLE"));

        assertThat(jdbc.queryForObject("select count(*) from audit_log where id = ?::uuid", Long.class, rowId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = ? and actor_id = ?::uuid "
                        + "and object_id is null", Long.class, AuditAction.AUDIT_LOG_TAMPER_ATTEMPTED.name(), admin.id()))
                .isEqualTo(1);
        assertThat(jdbc.queryForList("select details->>'method' from audit_log where action = ? "
                        + "and object_id = ?::uuid and actor_id = ?::uuid", String.class,
                AuditAction.AUDIT_LOG_TAMPER_ATTEMPTED.name(), rowId, admin.id()))
                .containsExactlyInAnyOrder("DELETE", "PUT", "PATCH");
        assertThatThrownBy(() -> jdbc.update("delete from audit_log where id = ?::uuid", rowId))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("update audit_log set action = 'LOGIN_FAILED' where id = ?::uuid", rowId))
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("TC-04: trường nội dung gửi tới bộ ghi bị loại bỏ, chỉ còn siêu dữ liệu và tên trường đã bỏ")
    void contentFieldsAreStripped() throws Exception {
        User admin = admin();
        User lan = learner();

        transactions.executeWithoutResult(status -> auditLogger.byUser(UUID.fromString(lan.id()),
                AuditAction.PROFILE_UPDATED, AuditObjectType.USER_ACCOUNT, UUID.fromString(lan.id()),
                Map.of("fields", List.of("displayName"), "content", "Bài ghi chép bí mật của Lan",
                        "draft", Map.of("note_text", "Câu trả lời riêng", "status", "SAVED")), null));

        search(admin, "account=" + lan.id() + "&action=PROFILE_UPDATED").andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].actor.id").value(lan.id()))
                .andExpect(jsonPath("$.items[0].objectType").value("USER_ACCOUNT"))
                .andExpect(jsonPath("$.items[0].objectId").value(lan.id()))
                .andExpect(jsonPath("$.items[0].outcome").value("SUCCESS"))
                .andExpect(jsonPath("$.items[0].details.fields[0]").value("displayName"))
                .andExpect(jsonPath("$.items[0].details.draft.status").value("SAVED"))
                .andExpect(jsonPath("$.items[0].details.content").doesNotExist())
                .andExpect(jsonPath("$.items[0].details.draft.note_text").doesNotExist())
                .andExpect(jsonPath("$.items[0].details.removedFields", hasSize(2)))
                .andExpect(jsonPath("$.items[0].details.removedFields", hasItem("content")))
                .andExpect(jsonPath("$.items[0].details.removedFields", hasItem("draft.note_text")));
        assertThat(jdbc.queryForObject("select count(*) from audit_log where details::text like '%bí mật%'",
                Long.class)).isZero();
    }

    @Test
    @DisplayName("TC-05: người học mở trang tra cứu nhật ký bị từ chối và lần từ chối được ghi lại")
    void learnersAreDeniedAndTheDenialIsLogged() throws Exception {
        User lan = learner();

        mvc.perform(get("/api/v1/admin/me").cookie(lan.cookies())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(withBrowser(get("/api/v1/admin/audit-logs")).cookie(lan.cookies()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/audit-logs/" + UUID.randomUUID()).with(csrf()).cookie(lan.cookies()))
                .andExpect(status().isForbidden());

        List<Map<String, Object>> denials = jdbc.queryForList("select object_type, details->>'method' as method, "
                + "details->>'path' as path, user_agent from audit_log where action = ? and actor_id = ?::uuid "
                + "order by occurred_at", AuditAction.ROLE_ACCESS_DENIED.name(), lan.id());
        assertThat(denials).hasSize(3);
        assertThat(denials).extracting(row -> (String) row.get("path"))
                .contains("/api/v1/admin/me", "/api/v1/admin/audit-logs")
                .anyMatch(path -> path.startsWith("/api/v1/admin/audit-logs/"));
        assertThat(denials).extracting(row -> row.get("method")).containsExactly("GET", "GET", "DELETE");
        assertThat(denials).extracting(row -> row.get("object_type")).containsOnly("ENDPOINT");
        assertThat(denials.get(1).get("user_agent")).isEqualTo(CHROME_ON_WINDOWS);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = ? and actor_id = ?::uuid",
                Long.class, AuditAction.AUDIT_LOG_TAMPER_ATTEMPTED.name(), lan.id())).isZero();
        mvc.perform(get("/api/v1/admin/audit-logs")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-06: mỗi lần tra cứu được ghi lại người tra, điều kiện lọc và thời điểm; địa chỉ thư thay bằng mã")
    void searchesAreLogged() throws Exception {
        User admin = admin();
        User lan = learner();

        search(admin, "account=" + lan.email().toUpperCase() + "&category=SESSION&from=2020-01-01T00:00:00Z")
                .andExpect(status().isOk());

        Map<String, Object> row = jdbc.queryForMap("select actor_type, object_type, occurred_at, details::text "
                + "as details from audit_log where action = ? and actor_id = ?::uuid",
                AuditAction.AUDIT_LOG_SEARCHED.name(), admin.id());
        assertThat(row.get("actor_type")).isEqualTo("USER");
        assertThat(row.get("object_type")).isEqualTo("AUDIT_LOG");
        assertThat(row.get("occurred_at")).isNotNull();
        assertThat((String) row.get("details")).contains(lan.id(), "SESSION", "2020-01-01T00:00:00Z")
                .doesNotContain(lan.email());

        search(admin, "action=AUDIT_LOG_SEARCHED&account=" + admin.id())
                .andExpect(jsonPath("$.items[0].action").value("AUDIT_LOG_SEARCHED"))
                .andExpect(jsonPath("$.items[0].category").value("ADMIN"));
    }

    @Test
    @DisplayName("Thay đổi cấu hình ghi thêm giá trị trước và sau, chỉ các trường thật sự đổi")
    void configurationChangesKeepBeforeAndAfter() throws Exception {
        User admin = admin();
        User lan = learner();
        changePreferences(lan, "{\"themeMode\":\"DARK\",\"fontSize\":\"MEDIUM\"}").andExpect(status().isOk());
        changePreferences(lan, "{\"themeMode\":\"DARK\"}").andExpect(status().isOk());

        search(admin, "account=" + lan.id() + "&action=UI_PREFERENCES_CHANGED")
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].category").value("SETTINGS"))
                .andExpect(jsonPath("$.items[0].objectType").value("UI_PREFERENCES"))
                .andExpect(jsonPath("$.items[0].details.changes", hasSize(1)))
                .andExpect(jsonPath("$.items[0].details.changes[0].field").value("themeMode"))
                .andExpect(jsonPath("$.items[0].details.changes[0].before").value("SYSTEM"))
                .andExpect(jsonPath("$.items[0].details.changes[0].after").value("DARK"));
    }

    @Test
    @DisplayName("Kết quả mới nhất trước, phân trang theo con trỏ không trùng không sót; điều kiện sai bị từ chối")
    void pagingAndValidation() throws Exception {
        User admin = admin();
        User lan = learner();
        for (String theme : List.of("DARK", "LIGHT", "SYSTEM", "DARK")) {
            changePreferences(lan, "{\"themeMode\":\"" + theme + "\"}").andExpect(status().isOk());
        }

        String all = search(admin, "account=" + lan.id()).andReturn().getResponse().getContentAsString();
        List<String> everything = JsonPath.read(all, "$.items[*].id");
        String first = search(admin, "account=" + lan.id() + "&limit=3").andExpect(jsonPath("$.items", hasSize(3)))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(first, "$.nextCursor");
        String second = search(admin, "account=" + lan.id() + "&limit=3&before=" + cursor)
                .andReturn().getResponse().getContentAsString();
        List<String> paged = new ArrayList<>(JsonPath.<List<String>>read(first, "$.items[*].id"));
        paged.addAll(JsonPath.<List<String>>read(second, "$.items[*].id"));
        assertThat(paged).isEqualTo(everything.subList(0, paged.size()));
        assertThat(paged).doesNotHaveDuplicates();

        search(admin, "account=khong-co-ai@example.com").andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
        search(admin, "limit=0").andExpect(status().isBadRequest());
        search(admin, "limit=101").andExpect(status().isBadRequest());
        search(admin, "category=KHONG_CO").andExpect(status().isBadRequest());
        search(admin, "from=2026-10-02T00:00:00Z&to=2026-10-01T00:00:00Z").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Nhật ký lưu một năm: việc dọn đêm chỉ xóa dòng quá một năm, ngoài việc dọn thì không xóa được")
    void retentionRemovesOnlyRowsOlderThanAYear() {
        UUID old = UUID.randomUUID();
        UUID recent = UUID.randomUUID();
        String insert = "insert into audit_log (id, occurred_at, actor_type, action, object_type, details) "
                + "values (?, now() - (? || ' days')::interval, 'SYSTEM', 'DATA_EXPORT_EXPIRED', 'DATA_EXPORT', '{}')";
        jdbc.update(insert, old, "400");
        jdbc.update(insert, recent, "300");

        assertThatThrownBy(() -> jdbc.update("delete from audit_log where id = ?", old))
                .hasMessageContaining("append-only");
        assertThat(retentionJob.run()).isGreaterThanOrEqualTo(1);

        assertThat(jdbc.queryForObject("select count(*) from audit_log where id = ?", Long.class, old)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_log where id = ?", Long.class, recent)).isOne();
        assertThatThrownBy(() -> jdbc.update("delete from audit_log where id = ?", recent))
                .hasMessageContaining("append-only");
    }

    // ---------------------------------------------------------------- hỗ trợ

    private ResultActions search(User admin, String query) throws Exception {
        return mvc.perform(get("/api/v1/admin/audit-logs?" + query).cookie(admin.cookies()));
    }

    private ResultActions changePreferences(User user, String body) throws Exception {
        return mvc.perform(patch("/api/v1/ui-preferences").with(csrf()).cookie(user.cookies())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long auditRows(String actorId) {
        return jdbc.queryForObject("select count(*) from audit_log where actor_id = ?::uuid", Long.class, actorId);
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

    private User admin() throws Exception {
        User user = learner();
        jdbc.update("update user_accounts set role = 'ADMIN' where id = ?::uuid", user.id());
        mvc.perform(get("/api/v1/admin/me").cookie(user.cookies())).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.email()));
        return user;
    }

    private User learner() throws Exception {
        String email = "nhat.ky." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
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

    private User login(String email) throws Exception {
        List<Cookie> jar = new ArrayList<>();
        absorb(jar, mvc.perform(withBrowser(post("/api/v1/auth/login")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(email, PASSWORD)))
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
