package com.learningcompanion.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.learningcompanion.library.infrastructure.web.MockWebPageFetcher;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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

/**
 * NCL-05-CN-004 — thêm tài liệu từ trang web và văn bản dán, kiểm qua API, tác vụ trích văn bản và cơ sở dữ liệu thật.
 * Trang web lấy qua bản mô phỏng của lớp kết nối (QTN-04); phần chống SSRF chạy như môi trường thật.
 */
@SpringBootTest(properties = "app.library.web-fetch.mode=mock")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class WebImportIT {

    private static final String PASSWORD = "them-tai-lieu-tu-trang-web-2026";
    private static final String SOURCES = "/api/v1/library/sources";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMailGateway mailbox;

    @BeforeEach
    void fresh() throws Exception {
        mvc.perform(post("/api/v1/dev/quota/reset")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("TC-01: dán đường dẫn bài viết vào thư mục: tiêu đề bài, nội dung chính không quảng cáo, sẵn sàng")
    void importsArticleIntoFolder() throws Exception {
        User learner = learner();
        String folder = folder(learner, "Cấu trúc dữ liệu");

        String body = web(learner, MockWebPageFetcher.SORTING_ARTICLE, folder)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Thuật toán sắp xếp: nổi bọt, chèn và nhanh"))
                .andExpect(jsonPath("$.type").value("WEB"))
                .andExpect(jsonPath("$.folderId").value(folder))
                .andExpect(jsonPath("$.sourceUrl").value(MockWebPageFetcher.SORTING_ARTICLE))
                .andExpect(jsonPath("$.fetchedAt").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        Map<String, Object> document = awaitStatus(learner, id, "READY");
        assertThat(document.get("sourceUrl")).isEqualTo(MockWebPageFetcher.SORTING_ARTICLE);
        String chunks = chunks(learner, id);
        String text = String.join("\n", JsonPath.<List<String>>read(chunks, "$[*].content"));
        assertThat(text).contains("Sắp xếp nổi bọt", "Sắp xếp chèn", "O(n log n)");
        assertThat(text).doesNotContain("Quảng cáo", "Tài trợ", "Trang chủ", "Bài liên quan", "Bản quyền",
                "tracker");
        assertThat(JsonPath.<List<String>>read(chunks, "$[*].location.kind")).containsOnly("PARAGRAPH");
        // Dung lượng tính cho bản chụp đã lưu, giống tệp tải lên.
        Long size = jdbc.queryForObject("select size_bytes from documents where id = ?::uuid", Long.class, id);
        assertThat(size).isPositive();
    }

    @Test
    @DisplayName("TC-02: dán ghi chép ba nghìn chữ kèm tiêu đề: tài liệu văn bản chia đoạn đúng thứ tự")
    void createsDocumentFromPastedText() throws Exception {
        User learner = learner();
        String text = IntStream.rangeClosed(1, 30)
                .mapToObj(i -> "Đoạn " + i + ". " + "ghi chép bài giảng cấu trúc dữ liệu ".repeat(20).strip())
                .collect(Collectors.joining("\n\n"));
        assertThat(text.split("\\s+").length).isGreaterThanOrEqualTo(3000);

        String body = mvc.perform(post(SOURCES + "/text").with(csrf()).cookie(learner.cookies())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Ghi chép bài giảng tuần 3", "text", text))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Ghi chép bài giảng tuần 3"))
                .andExpect(jsonPath("$.type").value("TEXT"))
                .andExpect(jsonPath("$.folderId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        awaitStatus(learner, id, "READY");
        String chunks = chunks(learner, id);
        List<String> contents = JsonPath.read(chunks, "$[*].content");
        String joined = String.join("\n", contents);
        int last = -1;
        for (int i = 1; i <= 30; i++) {
            int at = joined.indexOf("Đoạn " + i + ".");
            assertThat(at).as("đoạn %d theo đúng thứ tự", i).isGreaterThan(last);
            last = at;
        }
        List<Integer> starts = JsonPath.read(chunks, "$[*].location.number");
        assertThat(starts).isSorted();
    }

    @Test
    @DisplayName("Văn bản dán thiếu tiêu đề hay để trống bị từ chối; văn bản trùng báo đã có trong kho")
    void validatesPastedText() throws Exception {
        User learner = learner();
        paste(learner, "", "Nội dung").andExpect(status().isBadRequest());
        paste(learner, "Tiêu đề", "   \n ").andExpect(status().isBadRequest());
        String created = paste(learner, "Bản một", "Nội dung giống hệt nhau.").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        // Chờ tác vụ nền xử lý xong để không ghi lời gọi AI chồng sang lớp kiểm thử khác.
        awaitStatus(learner, JsonPath.read(created, "$.id"), "READY");
        paste(learner, "Bản hai", "Nội dung giống hệt nhau.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LIBRARY_DOCUMENT_DUPLICATE"));
    }

    @Test
    @DisplayName("TC-03: đường dẫn mạng nội bộ bị từ chối, không tạo tài liệu; giao thức khác http/https không hợp lệ")
    void rejectsInternalAddresses() throws Exception {
        User learner = learner();
        for (String url : List.of("http://127.0.0.1:8080/actuator", "http://localhost/admin",
                "http://169.254.169.254/latest/meta-data/", "http://192.168.1.1/", "http://[::1]/",
                "http://db.internal/", MockWebPageFetcher.REDIRECT_TO_INTERNAL)) {
            web(learner, url, null)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("WEB_ADDRESS_BLOCKED"));
        }
        for (String url : List.of("file:///etc/passwd", "ftp://example.com/a", "khong phai duong dan")) {
            web(learner, url, null)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("WEB_ADDRESS_INVALID"));
        }
        assertThat(documentCount(learner)).isZero();
    }

    @Test
    @DisplayName("TC-04: trang yêu cầu đăng nhập: báo không lấy được kèm lý do để gợi ý dán văn bản")
    void reportsLoginWall() throws Exception {
        User learner = learner();
        web(learner, MockWebPageFetcher.LOGIN_WALL, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("WEB_PAGE_UNAVAILABLE"))
                .andExpect(jsonPath("$.details.reason").value("LOGIN_REQUIRED"));
        web(learner, MockWebPageFetcher.MISSING, null)
                .andExpect(jsonPath("$.details.reason").value("NOT_FOUND"));
        web(learner, MockWebPageFetcher.SLOW, null)
                .andExpect(jsonPath("$.details.reason").value("TIMEOUT"));
        web(learner, MockWebPageFetcher.NOT_HTML, null)
                .andExpect(jsonPath("$.details.reason").value("UNSUPPORTED"));
        assertThat(documentCount(learner)).isZero();
    }

    @Test
    @DisplayName("TC-05: thêm vào thư mục của người khác: 404, ghi nhật ký lần từ chối, không lấy trang")
    void rejectsForeignFolder() throws Exception {
        User owner = learner();
        User intruder = learner();
        String folder = folder(owner, "Thư mục riêng");

        web(intruder, MockWebPageFetcher.SORTING_ARTICLE, folder).andExpect(status().isNotFound());
        mvc.perform(post(SOURCES + "/text").with(csrf()).cookie(intruder.cookies())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Chen vao", "text", "Nội dung", "folderId", folder))))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'LEARNING_ACCESS_DENIED' "
                + "and object_type = 'LIBRARY_FOLDER' and actor_id = ?::uuid and object_id = ?::uuid", Integer.class,
                intruder.id(), folder)).isEqualTo(2);
        assertThat(documentCount(intruder)).isZero();
        assertThat(documentCount(owner)).isZero();
    }

    @Test
    @DisplayName("Cùng một bài thêm lần hai được báo đã có trong kho")
    void reportsDuplicateArticle() throws Exception {
        User learner = learner();
        String created = web(learner, MockWebPageFetcher.SORTING_ARTICLE, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        awaitStatus(learner, JsonPath.read(created, "$.id"), "READY");
        web(learner, MockWebPageFetcher.SORTING_ARTICLE, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LIBRARY_DOCUMENT_DUPLICATE"));
    }

    private ResultActions web(User learner, String url, String folder) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("url", url);
        if (folder != null) {
            body.put("folderId", folder);
        }
        return mvc.perform(post(SOURCES + "/web").with(csrf()).cookie(learner.cookies())
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private ResultActions paste(User learner, String title, String text) throws Exception {
        return mvc.perform(post(SOURCES + "/text").with(csrf()).cookie(learner.cookies())
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("title", title, "text", text))));
    }

    private int documentCount(User learner) {
        Integer count = jdbc.queryForObject("select count(*) from documents where owner_id = ?::uuid", Integer.class,
                learner.id());
        return count == null ? 0 : count;
    }

    private String folder(User learner, String name) throws Exception {
        String withEmail = "{\"email\":\"" + learner.email() + "\",\"folders\":[\"" + name + "\"]}";
        String seeded = mvc.perform(post("/api/v1/dev/library/seed").contentType(MediaType.APPLICATION_JSON)
                        .content(withEmail))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(seeded, "$.folders['" + name + "']");
    }

    private Map<String, Object> awaitStatus(User learner, String id, String expected) {
        return await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(200)).until(() -> {
            String body = mvc.perform(get("/api/v1/library/documents/" + id).param("track", "false")
                    .cookie(learner.cookies())).andReturn().getResponse().getContentAsString();
            return JsonPath.<Map<String, Object>>read(body, "$");
        }, document -> expected.equals(document.get("status")));
    }

    private String chunks(User learner, String id) throws Exception {
        return mvc.perform(get("/api/v1/library/documents/" + id + "/chunks").cookie(learner.cookies()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static String json(Map<String, ?> values) {
        return values.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":\"" + escape(String.valueOf(entry.getValue())) + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
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

    private User learner() throws Exception {
        String email = "trang-web." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Minh Thư","email":"%s","password":"%s","acceptTerms":true,
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
