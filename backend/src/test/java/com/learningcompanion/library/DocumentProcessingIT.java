package com.learningcompanion.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.learningcompanion.devtools.SampleDocuments;
import com.learningcompanion.shared.mail.MockMailGateway;
import com.learningcompanion.support.PostgresTestConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
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

/**
 * NCL-05-CN-003 — trích văn bản, nhận dạng chữ, chia đoạn và lập chỉ mục tài liệu, kiểm qua API tải lên thật, tác vụ
 * nền, lớp kết nối mô phỏng và cơ sở dữ liệu thật.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class DocumentProcessingIT {

    private static final String PASSWORD = "xu-ly-tai-lieu-2026";
    private static final String LIBRARY = "/api/v1/library";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMailGateway mailbox;

    @BeforeEach
    void fresh() throws Exception {
        mvc.perform(post("/api/v1/dev/quota/reset")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/dev/ai/reset")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/dev/library/processing").contentType(MediaType.APPLICATION_JSON)
                .content("{\"running\":true}")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-01: bài giảng năm mươi trang sẵn sàng trong sáu mươi giây, mỗi đoạn gắn số trang, có thông báo")
    void processesFiftyPageLecture() throws Exception {
        User learner = learner();
        long ocrCalls = calls("DOCUMENT_OCR");
        long started = System.nanoTime();
        String id = upload(learner, "Giải tích 1.pdf", SampleDocuments.lecture(50, 12));

        Map<String, Object> document = awaitStatus(learner, id, "READY", Duration.ofSeconds(60));
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(60));
        assertThat(JsonPath.<Integer>read(document, "$.processing.units")).isEqualTo(50);
        assertThat(JsonPath.<Map<String, Object>>read(document, "$.processing")).doesNotContainKey("error");
        assertThat(JsonPath.<String>read(document, "$.contentText")).contains("Chuong 1").contains("Chuong 50");

        String chunks = chunks(learner, id);
        List<String> kinds = JsonPath.read(chunks, "$[*].location.kind");
        List<Integer> pages = JsonPath.read(chunks, "$[*].location.number");
        List<Integer> ends = JsonPath.read(chunks, "$[*].location.end");
        assertThat(kinds).isNotEmpty().containsOnly("PAGE");
        assertThat(ends).isEqualTo(pages);
        assertThat(pages).isSorted().containsAll(java.util.stream.IntStream.rangeClosed(1, 50).boxed().toList());
        assertThat(JsonPath.<Integer>read(document, "$.processing.chunks")).isEqualTo(kinds.size());
        // Đề mục của trang được ghi kèm đoạn; trang quét (trang 12) đi qua nhận dạng chữ.
        assertThat(JsonPath.<List<String>>read(chunks, "$[?(@.location.number == 3)].heading")).contains("Chuong 3");
        assertThat(JsonPath.<List<String>>read(chunks, "$[?(@.location.number == 12)].source")).containsOnly("OCR");
        assertThat(JsonPath.<List<String>>read(chunks, "$[?(@.location.number == 11)].source")).containsOnly("TEXT");
        assertThat(calls("DOCUMENT_OCR") - ocrCalls).isEqualTo(1);

        // Vector lưu tách theo chủ sở hữu; lời gọi chạy theo phiên bản cấu hình đang phát hành.
        assertThat(jdbc.queryForObject("select count(*) from document_chunks where document_id = ?::uuid "
                + "and owner_id = ?::uuid and cardinality(embedding) > 0", Long.class, id, learner.id()))
                .isEqualTo(kinds.size());
        assertThat(jdbc.queryForList("select distinct config_version from ai_call_records where feature in "
                + "('DOCUMENT_OCR', 'DOCUMENT_EMBEDDING')", Integer.class)).containsOnly(1);

        // Người học nhận thông báo; xử lý tài liệu không tốn lượt trí tuệ nhân tạo.
        String notifications = mvc.perform(get("/api/v1/notifications").cookie(learner.cookies()))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(notifications, "$.items[?(@.type == 'DOCUMENT_READY')].params.title"))
                .containsExactly("Giải tích 1");
        // Bấm thông báo mở trang tài liệu.
        mvc.perform(post("/api/v1/notifications/" + JsonPath.<List<String>>read(notifications,
                        "$.items[?(@.type == 'DOCUMENT_READY')].id").getFirst() + "/open").with(csrf())
                        .cookie(learner.cookies()))
                .andExpect(jsonPath("$.path").value("/library/" + id));
        mvc.perform(get("/api/v1/quota").cookie(learner.cookies()))
                .andExpect(jsonPath("$.ai.usedUnits").value(0))
                .andExpect(jsonPath("$.ai.reservedUnits").value(0));

        // Mỗi tài liệu chỉ được xử lý một lần: nhận lại hay quét hàng chờ không xử lý thêm.
        mvc.perform(post(LIBRARY + "/documents/" + id + "/reprocess").with(csrf()).cookie(learner.cookies()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FAILED"));
        assertThat(jdbc.queryForObject("select processing_attempts from documents where id = ?::uuid", Integer.class,
                id)).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-02: hai ảnh chụp vở: văn bản nhận dạng được chia đoạn theo từng ảnh, mỗi đoạn trỏ về đúng ảnh gốc")
    void recognizesNotebookPhotos() throws Exception {
        User learner = learner();
        String first = upload(learner, "vo-trang-1.png", SampleDocuments.notebook(
                "# Đạo hàm\n\nĐạo hàm của x bình phương là 2x.\n\nQuy tắc chuỗi: (f(g(x)))' = f'(g(x)) g'(x)."));
        String second = upload(learner, "vo-trang-2.png", SampleDocuments.notebook(
                "# Tích phân\n\nTích phân là phép toán ngược của đạo hàm."));

        awaitStatus(learner, first, "READY", Duration.ofSeconds(30));
        awaitStatus(learner, second, "READY", Duration.ofSeconds(30));
        String one = chunks(learner, first);
        String two = chunks(learner, second);
        assertThat(JsonPath.<List<String>>read(one, "$[*].location.kind")).containsOnly("IMAGE");
        assertThat(JsonPath.<List<Integer>>read(one, "$[*].location.number")).containsOnly(1);
        assertThat(JsonPath.<List<String>>read(one, "$[*].source")).containsOnly("OCR");
        assertThat(JsonPath.<List<String>>read(one, "$[*].heading")).containsOnly("Đạo hàm");
        assertThat(String.join(" ", JsonPath.<List<String>>read(one, "$[*].content")))
                .contains("2x").contains("Quy tắc chuỗi").doesNotContain("Tích phân");
        assertThat(JsonPath.<List<String>>read(two, "$[*].heading")).containsOnly("Tích phân");
        assertThat(String.join(" ", JsonPath.<List<String>>read(two, "$[*].content")))
                .contains("phép toán ngược").doesNotContain("Quy tắc chuỗi");
    }

    @Test
    @DisplayName("TC-03: nhận dạng chữ lỗi ở trang 12: thử lại riêng trang đó, vẫn lỗi thì báo lỗi kèm lý do và thử lại được")
    void reportsFailingPageAndRetries() throws Exception {
        User learner = learner();
        mvc.perform(post("/api/v1/dev/ai/mock-ocr-failures").contentType(MediaType.APPLICATION_JSON)
                .content("{\"calls\":-1}")).andExpect(status().isNoContent());
        long failuresBefore = failures("DOCUMENT_OCR");
        String id = upload(learner, "Bài giảng quét.pdf", SampleDocuments.lecture(20, 12));

        Map<String, Object> document = awaitStatus(learner, id, "FAILED", Duration.ofSeconds(60));
        assertThat(JsonPath.<String>read(document, "$.processing.error")).isEqualTo("OCR_FAILED");
        assertThat(JsonPath.<Integer>read(document, "$.processing.errorPage")).isEqualTo(12);
        assertThat(JsonPath.<Boolean>read(document, "$.processing.retryable")).isTrue();
        // Lời gọi đầu và một lần thử lại riêng trang 12; không đoạn nào được lưu.
        assertThat(failures("DOCUMENT_OCR") - failuresBefore).isEqualTo(2);
        assertThat(JsonPath.<List<Object>>read(chunks(learner, id), "$")).isEmpty();
        String notifications = mvc.perform(get("/api/v1/notifications").cookie(learner.cookies()))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(notifications,
                "$.items[?(@.type == 'DOCUMENT_PROCESSING_FAILED')].params.page")).containsExactly(12);

        // Dịch vụ hoạt động lại, người học bấm thử lại: tài liệu sẵn sàng.
        mvc.perform(post("/api/v1/dev/ai/mock-ocr-failures").contentType(MediaType.APPLICATION_JSON)
                .content("{\"calls\":0}")).andExpect(status().isNoContent());
        mvc.perform(post(LIBRARY + "/documents/" + id + "/reprocess").with(csrf()).cookie(learner.cookies()))
                .andExpect(status().isOk());
        document = awaitStatus(learner, id, "READY", Duration.ofSeconds(60));
        assertThat(JsonPath.<Map<String, Object>>read(document, "$.processing")).doesNotContainKey("error");
        assertThat(JsonPath.<List<Integer>>read(chunks(learner, id), "$[?(@.source == 'OCR')].location.number"))
                .containsOnly(12);
    }

    @Test
    @DisplayName("Nhận dạng chữ lỗi một lần ở một trang: thử lại riêng trang đó thành công, tài liệu vẫn sẵn sàng")
    void recoversWhenPageRetrySucceeds() throws Exception {
        User learner = learner();
        // Lớp kết nối thử hai lần cho mỗi lời gọi (max_retries = 1): hai lỗi đầu làm hỏng lời gọi đầu tiên.
        mvc.perform(post("/api/v1/dev/ai/mock-ocr-failures").contentType(MediaType.APPLICATION_JSON)
                .content("{\"calls\":2}")).andExpect(status().isNoContent());
        String id = upload(learner, "Một trang quét.pdf", SampleDocuments.lecture(3, 2));
        awaitStatus(learner, id, "READY", Duration.ofSeconds(60));
        assertThat(JsonPath.<List<String>>read(chunks(learner, id), "$[?(@.location.number == 2)].source"))
                .containsOnly("OCR");
    }

    @Test
    @DisplayName("TC-04: tệp chỉ có trang trắng: báo không trích được văn bản, gợi ý tải bản rõ hơn, không gọi nhận dạng chữ")
    void reportsBlankDocument() throws Exception {
        User learner = learner();
        long ocrCalls = calls("DOCUMENT_OCR");
        String id = upload(learner, "trang-trang.pdf", SampleDocuments.blank(4));

        Map<String, Object> document = awaitStatus(learner, id, "FAILED", Duration.ofSeconds(30));
        assertThat(JsonPath.<String>read(document, "$.processing.error")).isEqualTo("NO_TEXT");
        assertThat(JsonPath.<Boolean>read(document, "$.processing.retryable")).isFalse();
        assertThat(JsonPath.<Integer>read(document, "$.processing.chunks")).isZero();
        assertThat(calls("DOCUMENT_OCR")).isEqualTo(ocrCalls);
    }

    @Test
    @DisplayName("TC-05: người học khác biết mã tài liệu: lấy đoạn văn hay thử lại đều 404, lần từ chối được ghi nhật ký")
    void hidesChunksFromOtherLearners() throws Exception {
        User owner = learner();
        User stranger = learner();
        String id = upload(owner, "Riêng tư.pdf", SampleDocuments.lecture(2, 0));
        awaitStatus(owner, id, "READY", Duration.ofSeconds(30));

        mvc.perform(get(LIBRARY + "/documents/" + id + "/chunks").cookie(stranger.cookies()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(post(LIBRARY + "/documents/" + id + "/reprocess").with(csrf()).cookie(stranger.cookies()))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForList("select details->>'reason' from audit_log where action = "
                        + "'LEARNING_ACCESS_DENIED' and object_type = 'DOCUMENT' and object_id = ?::uuid "
                        + "and actor_id = ?::uuid", String.class, id, stranger.id()))
                .containsExactly("NOT_OWNER", "NOT_OWNER");
        assertThat(JsonPath.<List<Object>>read(chunks(owner, id), "$")).isNotEmpty();
    }

    @Test
    @DisplayName("Tài liệu Word: đề mục, bảng và công thức giữ cấu trúc, đoạn gắn số đoạn văn")
    void processesWordDocument() throws Exception {
        User learner = learner();
        String id = upload(learner, "On tap.docx", ExtractorSamples.docx());
        awaitStatus(learner, id, "READY", Duration.ofSeconds(30));
        String chunks = chunks(learner, id);
        assertThat(JsonPath.<List<String>>read(chunks, "$[*].location.kind")).containsOnly("PARAGRAPH");
        assertThat(JsonPath.<List<String>>read(chunks, "$[*].heading")).contains("Chương 1. Giới hạn");
        assertThat(String.join("\n", JsonPath.<List<String>>read(chunks, "$[*].content")))
                .contains("Hàm số | Giới hạn")
                .contains("lim");
    }

    // ------------------------------------------------------------------ hỗ trợ

    private long calls(String feature) {
        return jdbc.queryForObject("select count(*) from ai_call_records where feature = ?", Long.class, feature);
    }

    private long failures(String feature) {
        return jdbc.queryForObject("select count(*) from ai_call_records where feature = ? and outcome <> 'SUCCESS'",
                Long.class, feature);
    }

    private Map<String, Object> awaitStatus(User learner, String id, String expected, Duration within) {
        return await().atMost(within).pollInterval(Duration.ofMillis(200)).until(() -> {
            String body = mvc.perform(get(LIBRARY + "/documents/" + id).param("track", "false")
                    .cookie(learner.cookies())).andReturn().getResponse().getContentAsString();
            return JsonPath.<Map<String, Object>>read(body, "$");
        }, document -> expected.equals(document.get("status")));
    }

    private String chunks(User learner, String id) throws Exception {
        return mvc.perform(get(LIBRARY + "/documents/" + id + "/chunks").cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** Tải trọn một tệp qua API tải lên theo phần; trả về mã tài liệu chờ xử lý vừa tạo. */
    private String upload(User learner, String name, byte[] file) throws Exception {
        String started = mvc.perform(post(LIBRARY + "/uploads").with(csrf()).cookie(learner.cookies())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"%s\",\"sizeBytes\":%d}".formatted(name, file.length)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String upload = JsonPath.read(started, "$.id");
        int chunk = JsonPath.read(started, "$.chunkSize");
        for (int index = 0, from = 0; from < file.length; index++, from += chunk) {
            mvc.perform(put(LIBRARY + "/uploads/" + upload + "/parts/" + index).with(csrf()).cookie(learner.cookies())
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .content(Arrays.copyOfRange(file, from, Math.min(file.length, from + chunk))))
                    .andExpect(status().isOk());
        }
        String document = mvc.perform(post(LIBRARY + "/uploads/" + upload + "/complete").with(csrf())
                        .cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(document, "$.id");
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
        String email = "xu-ly." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Bảo Anh","email":"%s","password":"%s","acceptTerms":true,
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
