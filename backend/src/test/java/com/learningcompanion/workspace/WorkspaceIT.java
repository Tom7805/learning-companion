package com.learningcompanion.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.learningcompanion.shared.mail.MockMailGateway;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** NCL-06-CN-001 — không gian học gắn tài liệu từ kho, kiểm qua API và cơ sở dữ liệu thật. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class WorkspaceIT {

    private static final String PASSWORD = "khong-gian-hoc-rieng-2026";
    private static final String WORKSPACES = "/api/v1/workspaces";
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
    }

    @Test
    @DisplayName("TC-01: đã có hai trăm không gian học, tạo không gian thứ hai trăm lẻ một vẫn bình thường")
    void createsBeyondTwoHundredWorkspaces() throws Exception {
        User learner = learner();
        mvc.perform(post("/api/v1/dev/workspaces/seed").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"count\":200}".formatted(learner.email())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaces", hasSize(200)));

        mvc.perform(json(post(WORKSPACES), learner, """
                        {"name":"  Kinh tế   lượng ","icon":"CHART","color":"SKY","description":"Ôn thi cuối kỳ"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Kinh tế lượng"))
                .andExpect(jsonPath("$.icon").value("CHART"))
                .andExpect(jsonPath("$.color").value("SKY"))
                .andExpect(jsonPath("$.description").value("Ôn thi cuối kỳ"))
                .andExpect(jsonPath("$.documentCount").value(0));

        mvc.perform(get(WORKSPACES).cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(201)))
                // Không gian học vừa tạo đứng đầu danh sách.
                .andExpect(jsonPath("$[0].name").value("Kinh tế lượng"));
    }

    @Test
    @DisplayName("Tên bắt buộc, tối đa một trăm ký tự; biểu tượng và màu có mặc định; sửa được tên, hình, mô tả")
    void validatesAndEdits() throws Exception {
        User learner = learner();
        mvc.perform(json(post(WORKSPACES), learner, "{\"name\":\"   \"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(json(post(WORKSPACES), learner, "{\"name\":\"%s\"}".formatted("a".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
        mvc.perform(json(post(WORKSPACES), learner, "{\"name\":\"Toán\",\"icon\":\"ROCKET\"}"))
                .andExpect(status().isBadRequest());

        String id = id(mvc.perform(json(post(WORKSPACES), learner, "{\"name\":\"%s\"}".formatted("a".repeat(100))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.icon").value("BOOK"))
                .andExpect(jsonPath("$.color").value("SUN"))
                .andExpect(jsonPath("$.description").value("")));

        mvc.perform(json(patch(WORKSPACES + "/" + id), learner, """
                        {"name":"Xác suất","icon":"CALCULATOR","color":"MINT","description":"Chương 1 đến 4"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Xác suất"))
                .andExpect(jsonPath("$.icon").value("CALCULATOR"))
                .andExpect(jsonPath("$.color").value("MINT"));
        mvc.perform(get(WORKSPACES + "/" + id).cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Chương 1 đến 4"))
                .andExpect(jsonPath("$.lastOpenedAt").isNotEmpty());
    }

    @Test
    @DisplayName("TC-02: giáo trình đang gắn với Xác suất được gắn thêm vào Kinh tế lượng, dùng được ở cả hai, không tải"
            + " lại và dung lượng đã dùng không tăng")
    void sharesOneDocumentAcrossWorkspaces() throws Exception {
        User learner = learner();
        String document = seedDocuments(learner, "Giáo trình Thống kê").get(0);
        String probability = workspace(learner, "Xác suất");
        String econometrics = workspace(learner, "Kinh tế lượng");
        attach(learner, probability, document).andExpect(jsonPath("$.attached").value(1));
        long usedBefore = usedBytes(learner);
        int documentsBefore = count("select count(*) from documents where owner_id = ?::uuid", learner.id());

        attach(learner, econometrics, document)
                .andExpect(jsonPath("$.attached").value(1))
                .andExpect(jsonPath("$.alreadyAttached").value(0));
        // Gắn lại lần nữa không tạo thêm gì.
        attach(learner, econometrics, document)
                .andExpect(jsonPath("$.attached").value(0))
                .andExpect(jsonPath("$.alreadyAttached").value(1));

        for (String workspace : List.of(probability, econometrics)) {
            mvc.perform(get(WORKSPACES + "/" + workspace + "/documents").cookie(learner.cookies()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].document.id").value(document))
                    .andExpect(jsonPath("$[0].document.title").value("Giáo trình Thống kê"))
                    .andExpect(jsonPath("$[0].document.status").value("READY"))
                    .andExpect(jsonPath("$[0].attachedAt").isNotEmpty());
            mvc.perform(get(WORKSPACES + "/" + workspace).cookie(learner.cookies()))
                    .andExpect(jsonPath("$.documentCount").value(1));
        }
        assertThat(usedBytes(learner)).isEqualTo(usedBefore);
        assertThat(count("select count(*) from documents where owner_id = ?::uuid", learner.id()))
                .isEqualTo(documentsBefore);
        assertThat(count("select count(*) from workspace_documents where document_id = ?::uuid", document))
                .isEqualTo(2);

        // Khung thông tin tài liệu trong kho thấy cả hai không gian học đang dùng nó.
        mvc.perform(get(WORKSPACES + "/usage").param("documentId", document).cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Xác suất", "Kinh tế lượng")));
    }

    @Test
    @DisplayName("TC-03: xóa tài liệu đang được ba không gian học dùng thì bị báo trước tên ba không gian, chỉ xóa khi"
            + " xác nhận")
    void warnsBeforeTrashingSharedDocument() throws Exception {
        User learner = learner();
        List<String> documents = seedDocuments(learner, "Tài liệu dùng chung", "Tài liệu riêng");
        String shared = documents.get(0);
        for (String name : List.of("Xác suất", "Kinh tế lượng", "Thống kê")) {
            attach(learner, workspace(learner, name), shared);
        }

        mvc.perform(delete(LIBRARY + "/documents/" + shared).with(csrf()).cookie(learner.cookies()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LIBRARY_DOCUMENT_IN_USE"))
                .andExpect(jsonPath("$.details.workspaces[*].name",
                        containsInAnyOrder("Xác suất", "Kinh tế lượng", "Thống kê")));
        mvc.perform(json(post(LIBRARY + "/documents/bulk"), learner, """
                        {"action":"TRASH","documentIds":["%s","%s"]}""".formatted(shared, documents.get(1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.workspaces", hasSize(3)));
        assertThat(count("select count(*) from documents where owner_id = ?::uuid and trashed_at is not null",
                learner.id())).isZero();

        // Tài liệu không gắn với không gian học nào thì xóa như trước, không cần xác nhận thêm.
        mvc.perform(delete(LIBRARY + "/documents/" + documents.get(1)).with(csrf()).cookie(learner.cookies()))
                .andExpect(status().isOk());

        mvc.perform(delete(LIBRARY + "/documents/" + shared).param("confirmInUse", "true").with(csrf())
                        .cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trashBatchId").isNotEmpty());
        mvc.perform(get(LIBRARY + "/documents/" + shared).cookie(learner.cookies()))
                .andExpect(status().isNotFound());
        // Tài liệu trong thùng rác không còn hiện trong không gian học; liên kết được giữ để khôi phục.
        mvc.perform(get(WORKSPACES).cookie(learner.cookies()))
                .andExpect(jsonPath("$[*].documentCount", containsInAnyOrder(0, 0, 0)));
        assertThat(count("select count(*) from workspace_documents where document_id = ?::uuid", shared))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("TC-03: xóa thư mục chứa tài liệu đang dùng, hay xóa nhiều tài liệu, cũng phải xác nhận")
    void warnsBeforeTrashingFolderWithSharedDocument() throws Exception {
        User learner = learner();
        String seeded = seed(learner, """
                {"folders":["Học kỳ một/Thống kê"],
                 "documents":[{"title":"Bài giảng tuần 1","folder":"Học kỳ một/Thống kê"}]}""");
        String folder = JsonPath.read(seeded, "$.folders['Học kỳ một']");
        String document = JsonPath.<List<String>>read(seeded, "$.documents").get(0);
        attach(learner, workspace(learner, "Xác suất"), document);

        mvc.perform(get(WORKSPACES + "/usage").param("folderId", folder).cookie(learner.cookies()))
                .andExpect(jsonPath("$[0].name").value("Xác suất"));
        mvc.perform(delete(LIBRARY + "/folders/" + folder).with(csrf()).cookie(learner.cookies()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LIBRARY_DOCUMENT_IN_USE"))
                .andExpect(jsonPath("$.details.workspaces[0].name").value("Xác suất"));
        mvc.perform(delete(LIBRARY + "/folders/" + folder).param("confirmInUse", "true").with(csrf())
                        .cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents").value(1));
    }

    @Test
    @DisplayName("TC-04: gỡ tài liệu khỏi không gian học chỉ bỏ liên kết, tài liệu vẫn nguyên trong kho")
    void detachKeepsDocumentInLibrary() throws Exception {
        User learner = learner();
        String document = seedDocuments(learner, "Giáo trình Thống kê").get(0);
        String probability = workspace(learner, "Xác suất");
        String econometrics = workspace(learner, "Kinh tế lượng");
        attach(learner, probability, document);
        attach(learner, econometrics, document);
        long usedBefore = usedBytes(learner);

        mvc.perform(delete(WORKSPACES + "/" + probability + "/documents/" + document).with(csrf())
                        .cookie(learner.cookies()))
                .andExpect(status().isNoContent());
        // Gỡ lần nữa không lỗi.
        mvc.perform(delete(WORKSPACES + "/" + probability + "/documents/" + document).with(csrf())
                        .cookie(learner.cookies()))
                .andExpect(status().isNoContent());

        mvc.perform(get(WORKSPACES + "/" + probability + "/documents").cookie(learner.cookies()))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get(WORKSPACES + "/" + econometrics + "/documents").cookie(learner.cookies()))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get(LIBRARY + "/documents/" + document).param("track", "false").cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Giáo trình Thống kê"));
        mvc.perform(get(WORKSPACES + "/usage").param("documentId", document).cookie(learner.cookies()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Kinh tế lượng"));
        assertThat(usedBytes(learner)).isEqualTo(usedBefore);
    }

    @Test
    @DisplayName("TC-05: người học khác mở, sửa, xóa hay gắn tài liệu vào không gian học bằng mã thì nhận 404 và lần từ"
            + " chối được ghi nhật ký")
    void strangerGetsNotFoundAndIsAudited() throws Exception {
        User owner = learner();
        User stranger = learner();
        String workspace = workspace(owner, "Xác suất");
        String ownerDocument = seedDocuments(owner, "Riêng tư").get(0);
        String strangerDocument = seedDocuments(stranger, "Của người khác").get(0);
        attach(owner, workspace, ownerDocument);

        mvc.perform(get(WORKSPACES + "/" + workspace).cookie(stranger.cookies())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get(WORKSPACES + "/" + workspace + "/documents").cookie(stranger.cookies()))
                .andExpect(status().isNotFound());
        mvc.perform(json(patch(WORKSPACES + "/" + workspace), stranger, "{\"name\":\"Chiếm\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(json(post(WORKSPACES + "/" + workspace + "/documents"), stranger,
                        "{\"documentIds\":[\"%s\"]}".formatted(strangerDocument)))
                .andExpect(status().isNotFound());
        mvc.perform(delete(WORKSPACES + "/" + workspace).with(csrf()).cookie(stranger.cookies()))
                .andExpect(status().isNotFound());
        mvc.perform(post(WORKSPACES + "/" + workspace + "/default-folder").with(csrf()).cookie(stranger.cookies()))
                .andExpect(status().isNotFound());
        assertThat(count("select count(*) from audit_log where action = 'LEARNING_ACCESS_DENIED' "
                + "and object_type = 'WORKSPACE' and actor_id = ?::uuid and object_id = ?::uuid",
                stranger.id(), workspace)).isEqualTo(6);

        // Chủ không gian học cũng không gắn được tài liệu của người khác; không có liên kết nào được tạo.
        mvc.perform(json(post(WORKSPACES + "/" + workspace + "/documents"), owner,
                        "{\"documentIds\":[\"%s\"]}".formatted(strangerDocument)))
                .andExpect(status().isNotFound());
        assertThat(count("select count(*) from workspace_documents where document_id = ?::uuid", strangerDocument))
                .isZero();
        // Không gian học không lộ ra với người khác, kể cả qua tra cứu "đang dùng trong".
        mvc.perform(get(WORKSPACES).cookie(stranger.cookies())).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get(WORKSPACES + "/usage").param("documentId", ownerDocument).cookie(stranger.cookies()))
                .andExpect(jsonPath("$", hasSize(0)));
        // Không gian học không tồn tại: 404 nhưng không có gì để ghi nhật ký.
        mvc.perform(get(WORKSPACES + "/" + UUID.randomUUID()).cookie(stranger.cookies()))
                .andExpect(status().isNotFound());
        mvc.perform(get(WORKSPACES).with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Xóa không gian học: vào thùng rác, tài liệu vẫn ở kho và không còn tính là đang được dùng")
    void trashingWorkspaceKeepsDocuments() throws Exception {
        User learner = learner();
        String document = seedDocuments(learner, "Giáo trình Thống kê").get(0);
        String workspace = workspace(learner, "Xác suất");
        attach(learner, workspace, document);

        mvc.perform(delete(WORKSPACES + "/" + workspace).with(csrf()).cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trashBatchId").isNotEmpty());

        mvc.perform(get(WORKSPACES + "/" + workspace).cookie(learner.cookies())).andExpect(status().isNotFound());
        mvc.perform(get(WORKSPACES).cookie(learner.cookies())).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get(LIBRARY + "/documents/" + document).param("track", "false").cookie(learner.cookies()))
                .andExpect(status().isOk());
        mvc.perform(get(WORKSPACES + "/usage").param("documentId", document).cookie(learner.cookies()))
                .andExpect(jsonPath("$", hasSize(0)));
        assertThat(count("select count(*) from workspaces where id = ?::uuid and trashed_at is not null "
                + "and trash_batch_id is not null", workspace)).isEqualTo(1);
        // Tài liệu không còn gắn với không gian học đang hoạt động nào nên xóa không cần xác nhận thêm.
        mvc.perform(delete(LIBRARY + "/documents/" + document).with(csrf()).cookie(learner.cookies()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Thư mục mặc định trong kho: tạo một lần theo tên không gian học, trùng tên thì thêm số, bị xóa thì tạo"
            + " lại")
    void defaultFolderIsCreatedOnceAndRecreated() throws Exception {
        User learner = learner();
        seed(learner, "{\"folders\":[\"Xác suất\"]}");
        String workspace = workspace(learner, "Xác suất");

        String folder = id(mvc.perform(post(WORKSPACES + "/" + workspace + "/default-folder").with(csrf())
                        .cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Xác suất (2)")));
        mvc.perform(post(WORKSPACES + "/" + workspace + "/default-folder").with(csrf()).cookie(learner.cookies()))
                .andExpect(jsonPath("$.id").value(folder));

        mvc.perform(delete(LIBRARY + "/folders/" + folder).with(csrf()).cookie(learner.cookies()))
                .andExpect(status().isOk());
        String recreated = id(mvc.perform(post(WORKSPACES + "/" + workspace + "/default-folder").with(csrf())
                        .cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Xác suất (2)")));
        assertThat(recreated).isNotEqualTo(folder);
    }

    private String workspace(User user, String name) throws Exception {
        return id(mvc.perform(json(post(WORKSPACES), user, "{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated()));
    }

    private org.springframework.test.web.servlet.ResultActions attach(User user, String workspace, String document)
            throws Exception {
        return mvc.perform(json(post(WORKSPACES + "/" + workspace + "/documents"), user,
                "{\"documentIds\":[\"%s\"]}".formatted(document))).andExpect(status().isOk());
    }

    private List<String> seedDocuments(User user, String... titles) throws Exception {
        StringBuilder documents = new StringBuilder();
        for (String title : titles) {
            documents.append(documents.isEmpty() ? "" : ",").append("{\"title\":\"").append(title)
                    .append("\",\"type\":\"PDF\"}");
        }
        return JsonPath.read(seed(user, "{\"documents\":[" + documents + "]}"), "$.documents");
    }

    private String seed(User user, String body) throws Exception {
        String withEmail = body.strip().replaceFirst("^\\{", "{\"email\":\"" + user.email() + "\",");
        return mvc.perform(post("/api/v1/dev/library/seed").contentType(MediaType.APPLICATION_JSON).content(withEmail))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private long usedBytes(User user) throws Exception {
        String body = mvc.perform(get("/api/v1/quota").cookie(user.cookies())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.storage.usedBytes")).longValue();
    }

    private int count(String sql, Object... args) {
        Integer found = jdbc.queryForObject(sql, Integer.class, args);
        return found == null ? 0 : found;
    }

    private static String id(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
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
        String email = "kgh." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
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
