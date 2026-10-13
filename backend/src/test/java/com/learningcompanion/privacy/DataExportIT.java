package com.learningcompanion.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.learningcompanion.privacy.application.DataExportJob;
import com.learningcompanion.privacy.application.DataExportService;
import com.learningcompanion.privacy.application.ExportPackager;
import com.learningcompanion.shared.export.DataExportContributor;
import com.learningcompanion.shared.mail.MockMailGateway;
import com.learningcompanion.shared.time.AdjustableClock;
import com.learningcompanion.support.PostgresTestConfiguration;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
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

/** NCL-01-CN-008 — xuất toàn bộ dữ liệu học tập bằng định dạng mở, kiểm qua API với PostgreSQL thật. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class DataExportIT {

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
    private DataExportJob job;

    @Autowired
    private ApplicationContext context;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    @DisplayName("TC-01: gói có đủ mọi loại dữ liệu ở định dạng mở, tài liệu trong thư mục thư viện, kèm tệp hướng dẫn"
            + " đọc; thư báo có đường dẫn tải")
    void packageContainsEveryKindOfData() throws Exception {
        Learner lan = learner();
        sample(lan.email(), "Ghi chú: Định luật Ohm");
        sample(lan.email(), "Ghi chú: Định luật Ohm");
        setConsent(lan, "PRODUCT_NEWS_EMAIL", true);

        String id = id(requestExport(lan).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.latest.status").value(org.hamcrest.Matchers.oneOf("QUEUED", "RUNNING", "READY")))
                .andExpect(jsonPath("$.canRequest").value(false)), "$.latest.id");
        JsonNode ready = awaitReady(lan, id);
        assertThat(ready.get("fileName").asString()).matches("learning-companion-export-\\d{4}-\\d{2}-\\d{2}\\.zip");
        assertThat(values(ready.get("sections"), "id")).containsExactly("account", "profile", "appearance", "privacy",
                "library");

        MockMailGateway.CapturedMail mail = awaitMail(lan.email(), "DATA_EXPORT_READY");
        assertThat(String.join(" ", mail.links())).contains("/exports/" + id);
        assertThat(mail.html()).contains("Thư viện tài liệu").contains("nhập lại mật khẩu");

        MvcResult file = mvc.perform(get(downloadUrl(lan, id, null))).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andReturn();
        assertThat(file.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment").contains(ready.get("fileName").asString());
        Map<String, byte[]> zip = unzip(file.getResponse().getContentAsByteArray());
        String root = ready.get("fileName").asString().replace(".zip", "") + "/";

        assertThat(zip.keySet()).contains(root + "README.txt", root + "manifest.json",
                root + "account/account.json", root + "account/sessions.csv",
                root + "profile/profile.json", root + "appearance/appearance.json",
                root + "privacy/consents.json", root + "privacy/consent-history.csv",
                root + "library/documents.json",
                root + "library/files/dinh-luat-ohm.txt", root + "library/files/dinh-luat-ohm (2).txt",
                root + "library/text/Ghi chú_ Định luật Ohm.md", root + "library/text/Ghi chú_ Định luật Ohm (2).md");

        String readme = text(zip, root + "README.txt");
        assertThat(readme).contains(lan.email()).contains("Thư viện tài liệu").contains("Quyền riêng tư và đồng ý")
                .contains("ISO 8601").contains("7 ngày");
        JsonNode manifest = JSON.readTree(zip.get(root + "manifest.json"));
        assertThat(manifest.get("format").asString()).isEqualTo("learning-companion-export");
        assertThat(sectionById(manifest.get("sections"), "library").get("files").toString())
                .contains("library/files/dinh-luat-ohm.txt");

        JsonNode account = JSON.readTree(zip.get(root + "account/account.json"));
        assertThat(account.get("email").asString()).isEqualTo(lan.email());
        assertThat(text(zip, root + "account/account.json")).doesNotContainIgnoringCase("passwordHash")
                .doesNotContain("$argon2").doesNotContain("$2a$");
        assertThat(JSON.readTree(zip.get(root + "profile/profile.json")).get("displayName").asString())
                .isEqualTo("Lan Anh");

        byte[] history = zip.get(root + "privacy/consent-history.csv");
        assertThat(history).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(new String(history, StandardCharsets.UTF_8))
                .contains("recorded_at,purpose,decision,document_version")
                .contains("PRODUCT_NEWS_EMAIL,GRANTED");

        JsonNode documents = JSON.readTree(zip.get(root + "library/documents.json"));
        assertThat(values(documents, "file"))
                .containsExactlyInAnyOrder("files/dinh-luat-ohm.txt", "files/dinh-luat-ohm (2).txt");
        assertThat(text(zip, root + "library/files/dinh-luat-ohm.txt")).contains("Định luật Ohm").contains("I = U / R");
        assertThat(text(zip, root + "library/text/Ghi chú_ Định luật Ohm.md"))
                .startsWith("# Ghi chú: Định luật Ohm").contains("../files/dinh-luat-ohm.txt");
    }

    @Test
    @DisplayName("TC-02: việc xuất không đi qua mô-đun trí tuệ nhân tạo nên không tính và không bị chặn bởi hạn mức AI")
    void exportNeverTouchesAiQuota() {
        List<Class<?>> pipeline = new ArrayList<>(List.of(DataExportService.class, DataExportJob.class,
                ExportPackager.class));
        context.getBeansOfType(DataExportContributor.class).values()
                .forEach(contributor -> pipeline.add(AopUtils.getTargetClass(contributor)));
        assertThat(pipeline).hasSizeGreaterThanOrEqualTo(7);
        for (Class<?> type : pipeline) {
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                assertThat(Stream.of(constructor.getParameterTypes()).map(Class::getName))
                        .as("%s không được dùng dịch vụ AI", type.getSimpleName())
                        .noneMatch(name -> name.startsWith("com.learningcompanion.ai."));
            }
        }
    }

    @Test
    @DisplayName("TC-03: đường dẫn quá bảy ngày báo hết hạn, tệp bị xóa và người học yêu cầu xuất lại được")
    void expiredPackageCanBeRequestedAgain() throws Exception {
        Learner lan = learner();
        String id = id(requestExport(lan).andExpect(status().isAccepted()), "$.latest.id");
        awaitReady(lan, id);
        assertThat(storageKey(id)).isNotNull();

        clock.advance(Duration.ofDays(7).minusMinutes(2));
        Learner again = login(lan.email());
        String stillSigned = downloadUrl(again, id, null);
        clock.advance(Duration.ofMinutes(3));

        // Đường dẫn ký vẫn trong năm phút nhưng gói đã quá bảy ngày: không mở được nữa.
        mvc.perform(get(stillSigned)).andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("EXPORT_EXPIRED"));
        mvc.perform(get("/api/v1/exports/" + id).cookie(again.cookies())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.sizeBytes").doesNotExist());
        download(again, id, null).andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("EXPORT_EXPIRED"));
        assertThat(storageKey(id)).isNull();
        assertThat(audits(id, "DATA_EXPORT_EXPIRED")).isEqualTo(1);

        overview(again).andExpect(jsonPath("$.canRequest").value(true))
                .andExpect(jsonPath("$.latest.status").value("EXPIRED"));
        String fresh = id(requestExport(again).andExpect(status().isAccepted()), "$.latest.id");
        assertThat(fresh).isNotEqualTo(id);
        awaitReady(again, fresh);
    }

    @Test
    @DisplayName("Gói quá hạn mà người học không quay lại vẫn bị dọn khỏi kho theo lịch")
    void sweepPurgesUnvisitedExpiredPackages() throws Exception {
        Learner lan = learner();
        String id = id(requestExport(lan), "$.latest.id");
        awaitReady(lan, id);

        clock.advance(Duration.ofDays(8));
        job.sweep();

        assertThat(jdbc.queryForObject("select status from data_exports where id = ?::uuid", String.class, id))
                .isEqualTo("EXPIRED");
        assertThat(storageKey(id)).isNull();
    }

    @Test
    @DisplayName("TC-04: người học khác biết mã gói nhận về không tồn tại và lần từ chối được ghi nhật ký")
    void otherLearnerCannotSeeOrDownloadPackage() throws Exception {
        Learner lan = learner();
        Learner minh = learner();
        String id = id(requestExport(lan), "$.latest.id");
        awaitReady(lan, id);

        mvc.perform(get("/api/v1/exports/" + id).cookie(minh.cookies())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        download(minh, id, PASSWORD).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_log where action = 'LEARNING_ACCESS_DENIED' and object_type = 'DATA_EXPORT'
                and object_id = ?::uuid and actor_id = ?::uuid""", Long.class, id, minh.id())).isEqualTo(2);

        // Mã không tồn tại thì không ghi gì, chữ ký giả thì như tệp không tồn tại; chưa đăng nhập thì 401.
        mvc.perform(get("/api/v1/exports/" + UUID.randomUUID()).cookie(minh.cookies()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/exports/files/gia-mao.abc")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/exports")).andExpect(status().isUnauthorized());
        overview(minh).andExpect(jsonPath("$.latest").doesNotExist()).andExpect(jsonPath("$.canRequest").value(true));
    }

    @Test
    @DisplayName("TC-05: nhật ký ghi người yêu cầu, nội dung gói và thời điểm tạo gói")
    void auditRecordsActorContentAndTime() throws Exception {
        Learner lan = learner();
        sample(lan.email(), "Bài giảng chương 1");
        String id = id(requestExport(lan), "$.latest.id");
        awaitReady(lan, id);

        Map<String, Object> requested = jdbc.queryForMap("""
                select actor_type, actor_id::text as actor, occurred_at from audit_log
                where action = 'DATA_EXPORT_REQUESTED' and object_id = ?::uuid""", id);
        assertThat(requested.get("actor_type")).isEqualTo("USER");
        assertThat(requested.get("actor")).isEqualTo(lan.id());
        assertThat(requested.get("occurred_at")).isNotNull();

        Map<String, Object> completed = jdbc.queryForMap("""
                select actor_id::text as actor, occurred_at, details::text as details from audit_log
                where action = 'DATA_EXPORT_COMPLETED' and object_type = 'DATA_EXPORT' and object_id = ?::uuid""", id);
        assertThat(completed.get("actor")).isEqualTo(lan.id());
        assertThat(completed.get("occurred_at")).isNotNull();
        JsonNode details = JSON.readTree(completed.get("details").toString());
        assertThat(details.get("sections").toString()).contains("\"library\"").contains("\"account\"");
        assertThat(details.get("fileCount").asInt()).isGreaterThanOrEqualTo(8);
        assertThat(details.get("sizeBytes").asLong()).isPositive();
        // Nhật ký chỉ có mã và số đếm, không có nội dung học tập.
        assertThat(completed.get("details").toString()).doesNotContain("Bài giảng").doesNotContain("Ohm");
    }

    @Test
    @DisplayName("Mỗi ngày chỉ yêu cầu xuất được một lần; báo thời điểm yêu cầu tiếp")
    void oneRequestPerDay() throws Exception {
        Learner lan = learner();
        String id = id(requestExport(lan).andExpect(status().isAccepted()), "$.latest.id");

        requestExport(lan).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EXPORT_DAILY_LIMIT"))
                .andExpect(jsonPath("$.details.nextAvailableAt").exists())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        awaitReady(lan, id);
        overview(lan).andExpect(jsonPath("$.canRequest").value(false))
                .andExpect(jsonPath("$.nextAvailableAt").exists());

        clock.advance(Duration.ofDays(1));
        Learner tomorrow = login(lan.email());
        overview(tomorrow).andExpect(jsonPath("$.canRequest").value(true));
        String second = id(requestExport(tomorrow).andExpect(status().isAccepted()), "$.latest.id");
        awaitReady(tomorrow, second);
        // Gói mới thay gói cũ: gói cũ hết hạn ngay và tệp bị xóa.
        mvc.perform(get("/api/v1/exports/" + id).cookie(tomorrow.cookies()))
                .andExpect(jsonPath("$.status").value("EXPIRED"));
        assertThat(storageKey(id)).isNull();
    }

    @Test
    @DisplayName("Lần xuất bị kẹt ở luồng nền được coi là thất bại và không tính vào giới hạn mỗi ngày")
    void stalledExportDoesNotBlockNewRequest() throws Exception {
        Learner lan = learner();
        String stuck = UUID.randomUUID().toString();
        jdbc.update("insert into data_exports (id, owner_id, status, requested_at, started_at) values "
                        + "(?::uuid, ?::uuid, 'RUNNING', ?, ?)", stuck, lan.id(),
                java.sql.Timestamp.from(clock.instant().minus(Duration.ofHours(1))),
                java.sql.Timestamp.from(clock.instant().minus(Duration.ofHours(1))));

        overview(lan).andExpect(jsonPath("$.latest.status").value("FAILED"))
                .andExpect(jsonPath("$.canRequest").value(true));
        String id = id(requestExport(lan).andExpect(status().isAccepted()), "$.latest.id");
        awaitReady(lan, id);
    }

    @Test
    @DisplayName("Mỗi lần tải phải xác nhận lại: vừa đăng nhập thì tải ngay, quá năm phút thì cần mật khẩu đúng")
    void everyDownloadRequiresReauthentication() throws Exception {
        Learner lan = learner();
        String id = id(requestExport(lan), "$.latest.id");
        awaitReady(lan, id);
        mvc.perform(get("/api/v1/exports/" + id).cookie(lan.cookies()))
                .andExpect(jsonPath("$.reauthenticationRequired").value(false));
        download(lan, id, null).andExpect(status().isOk()).andExpect(jsonPath("$.url").exists());

        clock.advance(Duration.ofMinutes(6));
        mvc.perform(get("/api/v1/exports/" + id).cookie(lan.cookies()))
                .andExpect(jsonPath("$.reauthenticationRequired").value(true));
        download(lan, id, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REAUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.details.hasPassword").value(true));
        download(lan, id, "sai-mat-khau-roi").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
                .andExpect(jsonPath("$.details.remainingAttempts").value(4));
        String url = id(download(lan, id, PASSWORD).andExpect(status().isOk()), "$.url");
        mvc.perform(get(url)).andExpect(status().isOk());

        // Đường dẫn tải chỉ sống năm phút.
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        mvc.perform(get(url)).andExpect(status().isGone()).andExpect(jsonPath("$.code").value("FILE_LINK_EXPIRED"));
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_log where action = 'DATA_EXPORT_DOWNLOADED' and object_id = ?::uuid""",
                Long.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select download_count from data_exports where id = ?::uuid",
                Integer.class, id)).isEqualTo(2);
    }

    @Test
    @DisplayName("Gói chưa sẵn sàng thì chưa cấp đường dẫn tải")
    void packageNotReadyCannotBeDownloaded() throws Exception {
        Learner lan = learner();
        String id = UUID.randomUUID().toString();
        jdbc.update("insert into data_exports (id, owner_id, status, requested_at) values (?::uuid, ?::uuid, 'QUEUED', ?)",
                id, lan.id(), java.sql.Timestamp.from(clock.instant()));
        download(lan, id, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXPORT_NOT_READY"));
    }

    // ---------------------------------------------------------------- hỗ trợ

    private JsonNode awaitReady(Learner learner, String id) {
        return await().atMost(Duration.ofSeconds(60)).until(() -> JSON.readTree(
                        mvc.perform(get("/api/v1/exports/" + id).cookie(learner.cookies()))
                                .andReturn().getResponse().getContentAsString()),
                node -> "READY".equals(node.path("status").asString()));
    }

    private String downloadUrl(Learner learner, String id, String password) throws Exception {
        return id(download(learner, id, password).andExpect(status().isOk()), "$.url");
    }

    private ResultActions download(Learner learner, String id, String password) throws Exception {
        String body = password == null ? "{}" : "{\"password\":\"" + password + "\"}";
        return mvc.perform(post("/api/v1/exports/" + id + "/download").with(csrf()).cookie(learner.cookies())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions requestExport(Learner learner) throws Exception {
        return mvc.perform(post("/api/v1/exports").with(csrf()).cookie(learner.cookies()));
    }

    private ResultActions overview(Learner learner) throws Exception {
        return mvc.perform(get("/api/v1/exports").cookie(learner.cookies())).andExpect(status().isOk());
    }

    private ResultActions setConsent(Learner learner, String purpose, boolean granted) throws Exception {
        return mvc.perform(put("/api/v1/accounts/" + learner.id() + "/consents/" + purpose).with(csrf())
                .cookie(learner.cookies()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"granted\":" + granted + "}")).andExpect(status().isOk());
    }

    private static String id(ResultActions actions, String path) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString();
        JsonNode node = JSON.readTree(body);
        for (String part : path.substring(2).split("\\.")) {
            node = node.get(part);
        }
        return node.asString();
    }

    private static List<String> values(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            values.add(array.get(i).get(field).asString());
        }
        return values;
    }

    private static JsonNode sectionById(JsonNode sections, String id) {
        for (int i = 0; i < sections.size(); i++) {
            if (id.equals(sections.get(i).get("id").asString())) {
                return sections.get(i);
            }
        }
        throw new AssertionError("Gói không có phần " + id);
    }

    private String storageKey(String id) {
        return jdbc.queryForObject("select storage_key from data_exports where id = ?::uuid", String.class, id);
    }

    private long audits(String id, String action) {
        return jdbc.queryForObject("select count(*) from audit_log where action = ? and object_id = ?::uuid",
                Long.class, action, id);
    }

    private static Map<String, byte[]> unzip(byte[] content) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }

    private static String text(Map<String, byte[]> zip, String path) {
        assertThat(zip).containsKey(path);
        return new String(zip.get(path), StandardCharsets.UTF_8);
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

    private Learner learner() throws Exception {
        String email = "xuat.du.lieu." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Lan Anh","email":"%s","password":"%s","acceptTerms":true,
                                 "termsVersion":"2026-10-01","privacyVersion":"2026-10-01"}"""
                                .formatted(email, PASSWORD)))
                .andExpect(status().isAccepted());
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

    private void sample(String email, String title) throws Exception {
        mvc.perform(post("/api/v1/dev/library/sample").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"title\":\"" + title + "\"}"))
                .andExpect(status().isOk());
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
