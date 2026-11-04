package com.learningcompanion.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.learningcompanion.library.application.Fingerprints;
import com.learningcompanion.shared.mail.MockMailGateway;
import com.learningcompanion.shared.storage.TestFiles;
import com.learningcompanion.support.PostgresTestConfiguration;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.beans.factory.annotation.Value;
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

/**
 * NCL-05-CN-002 — tải tài liệu lên kho theo từng phần, kiểm qua API, kho tệp và cơ sở dữ liệu thật. Mỗi phần dài 1 KiB
 * để tệp nhỏ cũng đi qua nhiều phần.
 */
@SpringBootTest(properties = "app.uploads.chunk-size-bytes=1024")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class UploadIT {

    private static final String PASSWORD = "tai-len-kho-tai-lieu-2026";
    private static final String LIBRARY = "/api/v1/library";
    private static final int CHUNK = 1024;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMailGateway mailbox;

    @Value("${app.storage.local-root}")
    private String storageRoot;

    @BeforeEach
    void fresh() throws Exception {
        mvc.perform(post("/api/v1/dev/quota/reset")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("TC-01: kéo ba bài giảng vào thư mục Giải tích: ba tài liệu chờ xử lý, dung lượng đã dùng tăng đúng")
    void uploadsThreeLecturesIntoOpenFolder() throws Exception {
        User learner = learner();
        String seeded = seed(learner, "{\"folders\":[\"Học kỳ một/Giải tích\"]}");
        String calculus = JsonPath.read(seeded, "$.folders['Học kỳ một/Giải tích']");
        long usedBefore = usedBytes(learner);

        List<byte[]> files = List.of(TestFiles.pdf("Bài giảng 1"), TestFiles.pdf("Bài giảng 2"),
                TestFiles.pdf("Bài giảng 3"));
        long total = 0;
        for (int i = 0; i < files.size(); i++) {
            byte[] file = files.get(i);
            assertThat(file.length).isGreaterThan(CHUNK);
            total += file.length;
            upload(learner, "Bài giảng " + (i + 1) + ".pdf", file, calculus)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Bài giảng " + (i + 1)))
                    .andExpect(jsonPath("$.type").value("PDF"))
                    .andExpect(jsonPath("$.mimeType").value("application/pdf"))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.folderId").value(calculus))
                    .andExpect(jsonPath("$.sizeBytes").value(file.length));
        }

        mvc.perform(get(LIBRARY + "/documents").param("scope", "FOLDER").param("folderId", calculus)
                        .param("sort", "NAME").param("direction", "ASC").cookie(learner.cookies()))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].title").value("Bài giảng 1"))
                .andExpect(jsonPath("$[2].status").value("PENDING"));
        assertThat(usedBytes(learner)).isEqualTo(usedBefore + total);
        // Tệp được lưu mã hóa: trên đĩa không còn chữ ký của tài liệu dạng trang.
        String key = jdbc.queryForObject("select storage_key from documents where owner_id = ?::uuid limit 1",
                String.class, learner.id());
        byte[] stored = Files.readAllBytes(Path.of(storageRoot).resolve(key));
        assertThat(new String(stored, 0, 5)).isNotEqualTo("%PDF-");
        // Các phần tạm đã được dọn sau khi ghép.
        assertThat(Path.of(storageRoot, "uploads", learner.id())).isEmptyDirectory();
    }

    @Test
    @DisplayName("TC-02: tệp chứa mã độc mẫu bị từ chối với lý do có mã độc, không được lưu và được ghi nhật ký")
    void rejectsMalware() throws Exception {
        User learner = learner();
        byte[] eicar = TestFiles.ascii(TestFiles.EICAR);
        String upload = start(learner, "de-cuong.pdf", eicar.length, null, null);
        sendAll(learner, upload, eicar);

        mvc.perform(post(LIBRARY + "/uploads/" + upload + "/complete").with(csrf()).cookie(learner.cookies()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("UPLOAD_REJECTED"))
                .andExpect(jsonPath("$.details.reason").value("MALWARE"));

        assertThat(jdbc.queryForObject("select count(*) from documents where owner_id = ?::uuid", Long.class,
                learner.id())).isZero();
        mvc.perform(get(LIBRARY + "/uploads/" + upload).cookie(learner.cookies())).andExpect(status().isNotFound());
        assertThat(Path.of(storageRoot, "uploads", learner.id(), upload)).doesNotExist();
        assertThat(jdbc.queryForList("select details->>'reason' as reason, details->>'sizeBytes' as size from audit_log "
                        + "where action = 'DOCUMENT_UPLOAD_REJECTED' and actor_id = ?::uuid", learner.id()))
                .containsExactly(Map.of("reason", "MALWARE", "size", String.valueOf(eicar.length)));
    }

    @Test
    @DisplayName("TC-02: tệp có mật khẩu, tệp hỏng, tệp không hỗ trợ và tệp quá cỡ đều bị từ chối kèm lý do")
    void rejectsProtectedCorruptedUnsupportedAndOversizedFiles() throws Exception {
        User learner = learner();
        byte[] locked = TestFiles.pdf("Đề thi", "bi-mat");
        uploadExpectingRejection(learner, "de-thi.pdf", locked, "PASSWORD_PROTECTED");
        byte[] pdf = TestFiles.pdf("Bài giảng hỏng");
        uploadExpectingRejection(learner, "hong.pdf", Arrays.copyOf(pdf, pdf.length / 3), "CORRUPTED");
        byte[] executable = new byte[1500];
        executable[0] = 'M';
        executable[1] = 'Z';
        uploadExpectingRejection(learner, "bai-giang.pdf", executable, "UNSUPPORTED");

        jdbc.update("update quota_policies set max_upload_file_bytes = 1000");
        mvc.perform(json(post(LIBRARY + "/uploads"), learner, """
                        {"fileName":"to.pdf","sizeBytes":5000}"""))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"))
                .andExpect(jsonPath("$.details.maxBytes").value(1000));
        mvc.perform(get("/api/v1/quota").cookie(learner.cookies()))
                .andExpect(jsonPath("$.storage.maxFileBytes").value(1000));

        assertThat(jdbc.queryForList("select details->>'reason' from audit_log where action = "
                        + "'DOCUMENT_UPLOAD_REJECTED' and actor_id = ?::uuid order by occurred_at", String.class,
                learner.id()))
                .containsExactly("PASSWORD_PROTECTED", "CORRUPTED", "UNSUPPORTED", "TOO_LARGE");
        assertThat(jdbc.queryForObject("select count(*) from documents where owner_id = ?::uuid", Long.class,
                learner.id())).isZero();
    }

    @Test
    @DisplayName("TC-03: tải lại giáo trình đã có trong Giải tích vào Ôn thi: báo trùng kèm vị trí, không lưu thêm")
    void reportsDuplicateWithLocationOfExistingCopy() throws Exception {
        User learner = learner();
        String seeded = seed(learner, "{\"folders\":[\"Học kỳ một/Giải tích\",\"Ôn thi\"]}");
        String calculus = JsonPath.read(seeded, "$.folders['Học kỳ một/Giải tích']");
        String exam = JsonPath.read(seeded, "$.folders['Ôn thi']");
        byte[] textbook = TestFiles.pdf("Giáo trình chương một");
        String existing = JsonPath.read(upload(learner, "Giáo trình chương 1.pdf", textbook, calculus)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");
        long used = usedBytes(learner);

        // Giao diện gửi kèm dấu vân tay: báo trùng ngay, chưa phải gửi tệp.
        mvc.perform(json(post(LIBRARY + "/uploads"), learner, """
                        {"fileName":"giao-trinh.pdf","sizeBytes":%d,"folderId":"%s","sha256":"%s"}"""
                        .formatted(textbook.length, exam, Fingerprints.sha256(textbook))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LIBRARY_DOCUMENT_DUPLICATE"))
                .andExpect(jsonPath("$.details.documentId").value(existing))
                .andExpect(jsonPath("$.details.title").value("Giáo trình chương 1"))
                .andExpect(jsonPath("$.details.folderId").value(calculus))
                .andExpect(jsonPath("$.details.folderPath[0]").value("Học kỳ một"))
                .andExpect(jsonPath("$.details.folderPath[1]").value("Giải tích"));

        // Không có dấu vân tay (hoặc giao diện cũ): máy chủ tự tính khi hoàn tất và vẫn báo trùng.
        upload(learner, "giao-trinh-ban-sao.pdf", textbook, exam)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.documentId").value(existing));

        mvc.perform(get(LIBRARY + "/documents").param("scope", "FOLDER").param("folderId", exam)
                        .cookie(learner.cookies()))
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(usedBytes(learner)).isEqualTo(used);

        // Bản đã có vào thùng rác thì tải lại được.
        mvc.perform(delete(LIBRARY + "/documents/" + existing).with(csrf()).cookie(learner.cookies()))
                .andExpect(status().isOk());
        upload(learner, "Giáo trình chương 1.pdf", textbook, exam).andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-04: mạng mất khi tệp đang tải được một nửa; mạng có lại thì tải tiếp từ phần còn thiếu")
    void resumesFromMissingPart() throws Exception {
        User learner = learner();
        byte[] file = TestFiles.pdf("Tệp lớn mạng chập chờn " + "x".repeat(3000));
        int parts = (file.length + CHUNK - 1) / CHUNK;
        assertThat(parts).isGreaterThanOrEqualTo(4);
        String upload = start(learner, "Tệp lớn.pdf", file.length, null, null);
        int half = parts / 2;
        for (int index = 0; index < half; index++) {
            sendPart(learner, upload, file, index).andExpect(status().isOk());
        }
        // Phần kế tiếp gửi đi nhưng mất kết nối trước khi máy chủ nhận được; giao diện thử gửi tiếp phần sau đó.
        sendPart(learner, upload, file, half + 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UPLOAD_PART_OUT_OF_ORDER"))
                .andExpect(jsonPath("$.details.nextPart").value(half));
        mvc.perform(post(LIBRARY + "/uploads/" + upload + "/complete").with(csrf()).cookie(learner.cookies()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UPLOAD_INCOMPLETE"));

        // Mạng có lại: hỏi tiến độ, gửi lại phần vừa xong (trả lời bị mất) không làm sai tiến độ, rồi gửi tiếp.
        mvc.perform(get(LIBRARY + "/uploads/" + upload).cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedBytes").value(half * CHUNK))
                .andExpect(jsonPath("$.nextPart").value(half))
                .andExpect(jsonPath("$.partCount").value(parts));
        sendPart(learner, upload, file, half - 1).andExpect(jsonPath("$.receivedBytes").value(half * CHUNK));
        for (int index = half; index < parts - 1; index++) {
            sendPart(learner, upload, file, index).andExpect(status().isOk());
        }
        // Phần cuối ngắn hơn một phần đầy: nhận xong thì không còn phần nào thiếu.
        assertThat(file.length % CHUNK).isNotZero();
        sendPart(learner, upload, file, parts - 1)
                .andExpect(jsonPath("$.receivedBytes").value(file.length))
                .andExpect(jsonPath("$.nextPart").value(parts));
        String body = mvc.perform(post(LIBRARY + "/uploads/" + upload + "/complete").with(csrf())
                        .cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String documentId = JsonPath.read(body, "$.id");
        // Gửi lại yêu cầu hoàn tất (trả lời bị mất) nhận đúng tài liệu đó, không tạo bản thứ hai.
        mvc.perform(post(LIBRARY + "/uploads/" + upload + "/complete").with(csrf()).cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(documentId));

        // Tệp ghép lại giống hệt tệp gốc.
        String link = JsonPath.read(mvc.perform(post(LIBRARY + "/documents/" + documentId + "/file-link")
                        .with(csrf()).cookie(learner.cookies()))
                .andReturn().getResponse().getContentAsString(), "$.url");
        byte[] downloaded = mvc.perform(get(link).cookie(learner.cookies())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(file);
        assertThat(jdbc.queryForObject("select content_sha256 from documents where id = ?::uuid", String.class,
                documentId)).isEqualTo(Fingerprints.sha256(file));
    }

    @Test
    @DisplayName("TC-05: người học khác biết mã thư mục gửi tệp vào đó: không tìm thấy và lần từ chối được ghi")
    void foreignFolderIsNotFoundAndAudited() throws Exception {
        User owner = learner();
        User stranger = learner();
        String folder = JsonPath.read(seed(owner, "{\"folders\":[\"Giải tích\"]}"), "$.folders['Giải tích']");

        mvc.perform(json(post(LIBRARY + "/uploads"), stranger, """
                        {"fileName":"chen.pdf","sizeBytes":10,"folderId":"%s"}""".formatted(folder)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        assertThat(jdbc.queryForList("select actor_id::text as actor, details->>'reason' as reason from audit_log "
                        + "where action = 'LEARNING_ACCESS_DENIED' and object_type = 'LIBRARY_FOLDER' "
                        + "and object_id = ?::uuid", folder))
                .containsExactly(Map.of("actor", stranger.id(), "reason", "NOT_OWNER"));

        // Lần tải của người khác cũng như không tồn tại: không gửi phần, không hoàn tất, không hủy được.
        byte[] file = TestFiles.pdf("Riêng tư");
        String upload = start(owner, "rieng.pdf", file.length, folder, null);
        sendPart(stranger, upload, file, 0).andExpect(status().isNotFound());
        mvc.perform(get(LIBRARY + "/uploads/" + upload).cookie(stranger.cookies())).andExpect(status().isNotFound());
        mvc.perform(post(LIBRARY + "/uploads/" + upload + "/complete").with(csrf()).cookie(stranger.cookies()))
                .andExpect(status().isNotFound());
        mvc.perform(delete(LIBRARY + "/uploads/" + upload).with(csrf()).cookie(stranger.cookies()))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'LEARNING_ACCESS_DENIED' "
                + "and object_type = 'LIBRARY_UPLOAD' and object_id = ?::uuid and actor_id = ?::uuid", Long.class,
                upload, stranger.id())).isEqualTo(4);
        mvc.perform(get(LIBRARY + "/uploads/" + upload).cookie(owner.cookies()))
                .andExpect(jsonPath("$.receivedBytes").value(0));
    }

    @Test
    @DisplayName("Đổi tên tài liệu sau khi tải; tên tệp trùng trong thư mục được tự thêm số thứ tự; hủy lần tải dở")
    void renamesAfterUploadAndCancels() throws Exception {
        User learner = learner();
        String first = JsonPath.read(upload(learner, "Ghi chú.txt", "Đạo hàm\n".getBytes(), null)
                .andExpect(jsonPath("$.type").value("TEXT"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        upload(learner, "Ghi chú.md", "Tích phân\n".getBytes(), null)
                .andExpect(jsonPath("$.title").value("Ghi chú (2)"))
                .andExpect(jsonPath("$.mimeType").value("text/markdown; charset=utf-8"));
        upload(learner, "Ảnh chụp vở.png", TestFiles.png(40, 30), null)
                .andExpect(jsonPath("$.type").value("IMAGE"));
        mvc.perform(json(patch(LIBRARY + "/documents/" + first), learner, "{\"title\":\"Đạo hàm cơ bản\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Đạo hàm cơ bản"));

        byte[] file = TestFiles.pdf("Bỏ dở " + "y".repeat(2000));
        String upload = start(learner, "bo-do.pdf", file.length, null, null);
        sendPart(learner, upload, file, 0).andExpect(status().isOk());
        assertThat(Path.of(storageRoot, "uploads", learner.id(), upload)).isNotEmptyDirectory();
        mvc.perform(delete(LIBRARY + "/uploads/" + upload).with(csrf()).cookie(learner.cookies()))
                .andExpect(status().isNoContent());
        assertThat(Path.of(storageRoot, "uploads", learner.id(), upload)).doesNotExist();
        mvc.perform(get(LIBRARY + "/uploads/" + upload).cookie(learner.cookies())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Kho hết chỗ: chặn ngay từ lúc bắt đầu, không phải chờ gửi hết tệp")
    void storageFullIsRejectedUpFront() throws Exception {
        User learner = learner();
        jdbc.update("update quota_policies set storage_limit_bytes = 2000");
        mvc.perform(json(post(LIBRARY + "/uploads"), learner, """
                        {"fileName":"lon.pdf","sizeBytes":5000}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STORAGE_QUOTA_EXCEEDED"));
        assertThat(jdbc.queryForList("select details->>'reason' from audit_log where action = "
                + "'DOCUMENT_UPLOAD_REJECTED' and actor_id = ?::uuid", String.class, learner.id()))
                .containsExactly("STORAGE_FULL");
    }

    // ------------------------------------------------------------------ hỗ trợ

    private void uploadExpectingRejection(User learner, String name, byte[] file, String reason) throws Exception {
        upload(learner, name, file, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("UPLOAD_REJECTED"))
                .andExpect(jsonPath("$.details.reason").value(reason));
    }

    /** Tải trọn một tệp: bắt đầu, gửi mọi phần, hoàn tất; trả về kết quả của bước hoàn tất. */
    private ResultActions upload(User learner, String name, byte[] file, String folderId) throws Exception {
        String upload = start(learner, name, file.length, folderId, null);
        sendAll(learner, upload, file);
        return mvc.perform(post(LIBRARY + "/uploads/" + upload + "/complete").with(csrf()).cookie(learner.cookies()));
    }

    private String start(User learner, String name, long size, String folderId, String sha) throws Exception {
        String body = "{\"fileName\":\"%s\",\"sizeBytes\":%d%s%s}".formatted(name, size,
                folderId == null ? "" : ",\"folderId\":\"" + folderId + "\"",
                sha == null ? "" : ",\"sha256\":\"" + sha + "\"");
        return JsonPath.read(mvc.perform(json(post(LIBRARY + "/uploads"), learner, body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chunkSize").value(CHUNK))
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void sendAll(User learner, String upload, byte[] file) throws Exception {
        int parts = Math.max(1, (file.length + CHUNK - 1) / CHUNK);
        for (int index = 0; index < parts; index++) {
            sendPart(learner, upload, file, index).andExpect(status().isOk());
        }
    }

    private ResultActions sendPart(User learner, String upload, byte[] file, int index) throws Exception {
        int from = index * CHUNK;
        byte[] part = Arrays.copyOfRange(file, from, Math.min(file.length, from + CHUNK));
        return mvc.perform(put(LIBRARY + "/uploads/" + upload + "/parts/" + index).with(csrf())
                .cookie(learner.cookies()).contentType(MediaType.APPLICATION_OCTET_STREAM).content(part));
    }

    private long usedBytes(User learner) throws Exception {
        Number used = JsonPath.read(mvc.perform(get("/api/v1/quota").cookie(learner.cookies()))
                .andReturn().getResponse().getContentAsString(), "$.storage.usedBytes");
        return used.longValue();
    }

    private String seed(User user, String body) throws Exception {
        String withEmail = body.strip().replaceFirst("^\\{", "{\"email\":\"" + user.email() + "\",");
        return mvc.perform(post("/api/v1/dev/library/seed").contentType(MediaType.APPLICATION_JSON).content(withEmail))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, User user, String body) {
        return request.with(csrf()).cookie(user.cookies()).contentType(MediaType.APPLICATION_JSON).content(body);
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
        String email = "tai-len." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
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
