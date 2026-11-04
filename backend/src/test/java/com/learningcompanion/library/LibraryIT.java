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
import com.learningcompanion.shared.mail.MockMailGateway;
import com.learningcompanion.shared.time.AdjustableClock;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** NCL-05-CN-001 — kho tài liệu với thư mục nhiều cấp, nhãn và gắn sao, kiểm qua API và cơ sở dữ liệu thật. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class LibraryIT {

    private static final String PASSWORD = "kho-tai-lieu-ngan-nap-2026";
    private static final String LIBRARY = "/api/v1/library";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AdjustableClock clock;

    @Autowired
    private MockMailGateway mailbox;

    @BeforeEach
    void fresh() throws Exception {
        clock.reset();
        mvc.perform(post("/api/v1/dev/quota/reset")).andExpect(status().isNoContent());
    }

    @AfterEach
    void restore() {
        clock.reset();
    }

    @Test
    @DisplayName("TC-01: kéo hai tài liệu từ thư mục gốc vào Kinh tế vi mô, cây thư mục cập nhật ngay số tài liệu")
    void movesDocumentsIntoNestedFolder() throws Exception {
        User learner = learner();
        String seeded = seed(learner, """
                {"folders":["Học kỳ một/Kinh tế vi mô","Học kỳ một/Giải tích","Học kỳ một/Vật lý đại cương"],
                 "documents":[{"title":"Cung và cầu","type":"PDF"},{"title":"Độ co giãn","type":"PPTX"},
                              {"title":"Đạo hàm","type":"DOCX","folder":"Học kỳ một/Giải tích"}]}""");
        String micro = JsonPath.read(seeded, "$.folders['Học kỳ một/Kinh tế vi mô']");
        String semester = JsonPath.read(seeded, "$.folders['Học kỳ một']");
        List<String> ids = JsonPath.read(seeded, "$.documents");

        mvc.perform(get(LIBRARY + "/folders").cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folders.length()").value(4))
                .andExpect(jsonPath("$.folders[?(@.name == 'Kinh tế vi mô')].parentId").value(semester))
                .andExpect(jsonPath("$.rootDocumentCount").value(2))
                .andExpect(jsonPath("$.totalDocumentCount").value(3));

        mvc.perform(json(post(LIBRARY + "/documents/bulk"), learner, """
                        {"action":"MOVE","documentIds":["%s","%s"],"folderId":"%s"}"""
                        .formatted(ids.get(0), ids.get(1), micro)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affected").value(2));

        mvc.perform(get(LIBRARY + "/folders").cookie(learner.cookies()))
                .andExpect(jsonPath("$.folders[?(@.name == 'Kinh tế vi mô')].documentCount").value(2))
                .andExpect(jsonPath("$.rootDocumentCount").value(0))
                .andExpect(jsonPath("$.totalDocumentCount").value(3));
        mvc.perform(get(LIBRARY + "/documents").param("scope", "FOLDER").param("folderId", micro)
                        .param("sort", "NAME").cookie(learner.cookies()))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Cung và cầu"))
                .andExpect(jsonPath("$[1].title").value("Độ co giãn"))
                .andExpect(jsonPath("$[0].folderId").value(micro));
        mvc.perform(get(LIBRARY + "/documents").param("scope", "FOLDER").cookie(learner.cookies()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("TC-02: kho có ba trăm tài liệu, lọc loại trình chiếu và nhãn ôn thi thì chỉ còn đúng các tệp đó")
    void filtersThreeHundredDocumentsByTypeAndTag() throws Exception {
        User learner = learner();
        String[] types = {"PDF", "DOCX", "PPTX", "TEXT", "IMAGE", "WEB"};
        StringBuilder documents = new StringBuilder();
        int expected = 0;
        for (int i = 0; i < 300; i++) {
            String type = types[i % types.length];
            boolean exam = i % 4 == 0;
            if (type.equals("PPTX") && exam) {
                expected++;
            }
            documents.append(i == 0 ? "" : ",").append("""
                    {"title":"Tài liệu %03d","type":"%s","tags":%s,"status":"%s"}""".formatted(i, type,
                    exam ? "[\"Ôn thi\"]" : "[\"Đọc thêm\"]", i % 50 == 0 ? "PROCESSING" : "READY"));
        }
        String seeded = seed(learner, """
                {"tags":[{"name":"Ôn thi","color":"CORAL"},{"name":"Đọc thêm","color":"SKY"}],
                 "documents":[%s]}""".formatted(documents));
        String exam = JsonPath.read(seeded, "$.tags['Ôn thi']");
        assertThat(expected).isPositive();

        MvcResult result = mvc.perform(get(LIBRARY + "/documents").param("type", "PPTX").param("tag", exam)
                        .cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(expected))
                .andReturn();
        List<String> foundTypes = JsonPath.read(result.getResponse().getContentAsString(), "$[*].type");
        List<List<String>> foundTags = JsonPath.read(result.getResponse().getContentAsString(), "$[*].tagIds");
        assertThat(foundTypes).containsOnly("PPTX");
        assertThat(foundTags).allSatisfy(tagIds -> assertThat(tagIds).contains(exam));

        mvc.perform(get(LIBRARY + "/documents").cookie(learner.cookies())).andExpect(jsonPath("$.length()").value(300));
        mvc.perform(get(LIBRARY + "/documents").param("status", "PROCESSING").cookie(learner.cookies()))
                .andExpect(jsonPath("$.length()").value(6));
        mvc.perform(get(LIBRARY + "/tags").cookie(learner.cookies()))
                .andExpect(jsonPath("$[?(@.name == 'Ôn thi')].documentCount").value(75))
                .andExpect(jsonPath("$[?(@.name == 'Ôn thi')].color").value("CORAL"));
    }

    @Test
    @DisplayName("TC-03: thư mục con trùng tên trong cùng thư mục cha bị báo kèm tên gợi ý, thư mục cha khác thì được")
    void rejectsDuplicateFolderNameWithSuggestion() throws Exception {
        User learner = learner();
        String seeded = seed(learner, """
                {"folders":["Học kỳ một/Giải tích","Học kỳ hai"]}""");
        String semester = JsonPath.read(seeded, "$.folders['Học kỳ một']");
        String second = JsonPath.read(seeded, "$.folders['Học kỳ hai']");

        mvc.perform(get(LIBRARY + "/folders/name-check").param("parentId", semester).param("name", " giải  TÍCH ")
                        .cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.suggestion").value("giải TÍCH (2)"));
        mvc.perform(json(post(LIBRARY + "/folders"), learner, """
                        {"name":"Giải tích","parentId":"%s"}""".formatted(semester)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LIBRARY_NAME_TAKEN"))
                .andExpect(jsonPath("$.details.suggestion").value("Giải tích (2)"));
        assertThat(jdbc.queryForObject("select count(*) from library_folders where owner_id = ?::uuid "
                + "and lower(name) = lower('Giải tích')", Long.class, learner.id())).isEqualTo(1);

        mvc.perform(json(post(LIBRARY + "/folders"), learner, """
                        {"name":"Giải tích (2)","parentId":"%s"}""".formatted(semester)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(semester));
        mvc.perform(json(post(LIBRARY + "/folders"), learner, """
                        {"name":"Giải tích","parentId":"%s"}""".formatted(second)))
                .andExpect(status().isCreated());
        // Đổi tên hay chuyển thư mục tới chỗ đã có tên đó cũng bị báo trùng.
        List<String> otherCalculus = JsonPath.read(mvc.perform(get(LIBRARY + "/folders").cookie(learner.cookies()))
                .andReturn().getResponse().getContentAsString(),
                "$.folders[?(@.name == 'Giải tích' && @.parentId == '" + second + "')].id");
        mvc.perform(json(post(LIBRARY + "/folders/" + otherCalculus.get(0) + "/move"), learner, """
                        {"parentId":"%s"}""".formatted(semester)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LIBRARY_NAME_TAKEN"))
                .andExpect(jsonPath("$.details.suggestion").value("Giải tích (3)"));
        mvc.perform(json(post(LIBRARY + "/folders"), learner, "{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-04: xóa thư mục có mười tài liệu thì thư mục và mọi thứ bên trong vào thùng rác, biến khỏi cây")
    void trashesFolderWithContents() throws Exception {
        User learner = learner();
        StringBuilder documents = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            documents.append(i == 1 ? "" : ",").append("""
                    {"title":"Bài %d","folder":"%s"}""".formatted(i, i <= 7 ? "Học kỳ một" : "Học kỳ một/Giải tích"));
        }
        String seeded = seed(learner, """
                {"folders":["Học kỳ một/Giải tích","Học kỳ hai"],
                 "documents":[%s,{"title":"Ở thư mục gốc"}]}""".formatted(documents));
        String semester = JsonPath.read(seeded, "$.folders['Học kỳ một']");
        String calculus = JsonPath.read(seeded, "$.folders['Học kỳ một/Giải tích']");
        List<String> ids = JsonPath.read(seeded, "$.documents");

        mvc.perform(delete(LIBRARY + "/folders/" + semester).with(csrf()).cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folders").value(2))
                .andExpect(jsonPath("$.documents").value(10));

        mvc.perform(get(LIBRARY + "/folders").cookie(learner.cookies()))
                .andExpect(jsonPath("$.folders.length()").value(1))
                .andExpect(jsonPath("$.folders[0].name").value("Học kỳ hai"))
                .andExpect(jsonPath("$.totalDocumentCount").value(1));
        mvc.perform(get(LIBRARY + "/documents").cookie(learner.cookies()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Ở thư mục gốc"));
        mvc.perform(get(LIBRARY + "/documents/" + ids.get(0)).cookie(learner.cookies()))
                .andExpect(status().isNotFound());
        mvc.perform(get(LIBRARY + "/documents").param("scope", "FOLDER").param("folderId", calculus)
                        .cookie(learner.cookies()))
                .andExpect(status().isNotFound());

        // Cả đợt dùng chung một mã để thùng rác khôi phục được trọn vẹn; tệp gốc và dòng dữ liệu vẫn còn.
        List<Map<String, Object>> batches = jdbc.queryForList("select distinct trash_batch_id from ("
                + "select trash_batch_id from library_folders where owner_id = ?::uuid and trashed_at is not null "
                + "union all select trash_batch_id from documents where owner_id = ?::uuid and trashed_at is not null"
                + ") t", learner.id(), learner.id());
        assertThat(batches).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from documents where owner_id = ?::uuid", Long.class,
                learner.id())).isEqualTo(11);
        // Tên đã vào thùng rác không còn chặn tên mới.
        mvc.perform(json(post(LIBRARY + "/folders"), learner, "{\"name\":\"Học kỳ một\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("TC-05: người học khác mở thư mục bằng đường dẫn trực tiếp nhận không tìm thấy, lần từ chối được ghi")
    void foreignFolderIsNotFoundAndAudited() throws Exception {
        User owner = learner();
        User stranger = learner();
        String seeded = seed(owner, """
                {"folders":["Học kỳ một"],"tags":[{"name":"Ôn thi"}],
                 "documents":[{"title":"Nhật ký riêng","folder":"Học kỳ một","tags":["Ôn thi"]}]}""");
        String folder = JsonPath.read(seeded, "$.folders['Học kỳ một']");
        String tag = JsonPath.read(seeded, "$.tags['Ôn thi']");
        String document = JsonPath.read(seeded, "$.documents[0]");

        mvc.perform(get(LIBRARY + "/documents").param("scope", "FOLDER").param("folderId", folder)
                        .cookie(stranger.cookies()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        assertThat(jdbc.queryForList("select actor_id::text as actor, details->>'reason' as reason from audit_log "
                        + "where action = 'LEARNING_ACCESS_DENIED' and object_type = 'LIBRARY_FOLDER' "
                        + "and object_id = ?::uuid", folder))
                .containsExactly(Map.of("actor", stranger.id(), "reason", "NOT_OWNER"));

        // Mọi thao tác khác trên dữ liệu của người khác cũng như không tồn tại, và không đổi gì.
        mvc.perform(json(patch(LIBRARY + "/folders/" + folder), stranger, "{\"name\":\"Của tôi\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(LIBRARY + "/folders/" + folder).with(csrf()).cookie(stranger.cookies()))
                .andExpect(status().isNotFound());
        mvc.perform(json(post(LIBRARY + "/folders"), stranger, """
                        {"name":"Chen vào","parentId":"%s"}""".formatted(folder)))
                .andExpect(status().isNotFound());
        mvc.perform(json(patch(LIBRARY + "/tags/" + tag), stranger, "{\"name\":\"Đổi\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(json(post(LIBRARY + "/documents/bulk"), stranger, """
                        {"action":"TRASH","documentIds":["%s"]}""".formatted(document)))
                .andExpect(status().isNotFound());
        String strangerFolder = JsonPath.read(mvc.perform(json(post(LIBRARY + "/folders"), stranger,
                "{\"name\":\"Của tôi\"}")).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(json(post(LIBRARY + "/documents/bulk"), stranger, """
                        {"action":"MOVE","documentIds":["%s"],"folderId":"%s"}""".formatted(document, strangerFolder)))
                .andExpect(status().isNotFound());
        mvc.perform(get(LIBRARY + "/folders").cookie(stranger.cookies()))
                .andExpect(jsonPath("$.folders.length()").value(1))
                .andExpect(jsonPath("$.totalDocumentCount").value(0));
        mvc.perform(get(LIBRARY + "/documents/" + document).cookie(owner.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folderId").value(folder))
                .andExpect(jsonPath("$.tagIds[0]").value(tag));
        assertThat(jdbc.queryForObject("select name from library_folders where id = ?::uuid", String.class, folder))
                .isEqualTo("Học kỳ một");
        mvc.perform(get(LIBRARY + "/folders")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Nhãn màu, gắn sao, gần đây và cách sắp xếp theo tên tiếng Việt, dung lượng và lần mở")
    void tagsStarsRecentAndSorting() throws Exception {
        User learner = learner();
        String seeded = seed(learner, """
                {"documents":[{"title":"Đại số","text":"%s"},{"title":"anh văn","text":"ngắn"},
                              {"title":"Ăn uống","text":"vừa vừa"},{"title":"Dược lý","addedDaysAgo":10}]}"""
                .formatted("x".repeat(500)));
        List<String> ids = JsonPath.read(seeded, "$.documents");

        mvc.perform(get(LIBRARY + "/documents").param("sort", "NAME").cookie(learner.cookies()))
                .andExpect(jsonPath("$[*].title").value(org.hamcrest.Matchers.contains(
                        "anh văn", "Ăn uống", "Dược lý", "Đại số")));
        mvc.perform(get(LIBRARY + "/documents").param("sort", "SIZE").cookie(learner.cookies()))
                .andExpect(jsonPath("$[0].title").value("Đại số"));
        mvc.perform(get(LIBRARY + "/documents").param("from", clock.instant().minus(Duration.ofDays(2)).toString())
                        .cookie(learner.cookies()))
                .andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get(LIBRARY + "/documents").param("to", clock.instant().minus(Duration.ofDays(2)).toString())
                        .cookie(learner.cookies()))
                .andExpect(jsonPath("$[*].title").value(org.hamcrest.Matchers.contains("Dược lý")));

        // Tag: tạo, trùng tên (không phân biệt hoa thường), gắn nhiều nhãn cho một tài liệu.
        String exam = JsonPath.read(mvc.perform(json(post(LIBRARY + "/tags"), learner,
                        "{\"name\":\"Ôn thi\",\"color\":\"CORAL\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String key = JsonPath.read(mvc.perform(json(post(LIBRARY + "/tags"), learner, "{\"name\":\"Quan trọng\"}"))
                .andExpect(jsonPath("$.color").value("SUN")).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(json(post(LIBRARY + "/tags"), learner, "{\"name\":\"ôn THI\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LIBRARY_TAG_NAME_TAKEN"));
        mvc.perform(json(put(LIBRARY + "/documents/" + ids.get(0) + "/tags"), learner, """
                        {"tagIds":["%s","%s"]}""".formatted(exam, key)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tagIds.length()").value(2));
        mvc.perform(json(post(LIBRARY + "/documents/bulk"), learner, """
                        {"action":"ADD_TAGS","documentIds":["%s","%s"],"tagIds":["%s"]}"""
                        .formatted(ids.get(1), ids.get(2), exam)))
                .andExpect(jsonPath("$.affected").value(2));
        mvc.perform(get(LIBRARY + "/documents").param("tag", exam).param("tag", key).cookie(learner.cookies()))
                .andExpect(jsonPath("$[*].title").value(org.hamcrest.Matchers.contains("Đại số")));
        mvc.perform(delete(LIBRARY + "/tags/" + key).with(csrf()).cookie(learner.cookies()))
                .andExpect(status().isNoContent());
        mvc.perform(get(LIBRARY + "/tags").cookie(learner.cookies()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].documentCount").value(3));

        // Gắn sao và lối tắt "Có gắn sao".
        mvc.perform(json(put(LIBRARY + "/documents/" + ids.get(3) + "/star"), learner, "{\"starred\":true}"))
                .andExpect(jsonPath("$.starred").value(true));
        mvc.perform(get(LIBRARY + "/documents").param("scope", "STARRED").cookie(learner.cookies()))
                .andExpect(jsonPath("$[*].title").value(org.hamcrest.Matchers.contains("Dược lý")));
        mvc.perform(get(LIBRARY + "/folders").cookie(learner.cookies()))
                .andExpect(jsonPath("$.starredCount").value(1));

        // Chỉ xem khung thông tin thì không tính là một lần mở.
        mvc.perform(get(LIBRARY + "/documents/" + ids.get(2)).param("track", "false").cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastOpenedAt").doesNotExist());
        // Mở một tài liệu cũ thì nó lên đầu "Gần đây" và đầu cách xếp theo lần mở.
        clock.advance(Duration.ofMinutes(5));
        mvc.perform(get(LIBRARY + "/documents/" + ids.get(3)).cookie(learner.cookies()))
                .andExpect(jsonPath("$.lastOpenedAt").isNotEmpty());
        mvc.perform(get(LIBRARY + "/documents").param("scope", "RECENT").cookie(learner.cookies()))
                .andExpect(jsonPath("$[0].title").value("Dược lý"));
        mvc.perform(get(LIBRARY + "/documents").param("sort", "OPENED").cookie(learner.cookies()))
                .andExpect(jsonPath("$[0].title").value("Dược lý"))
                .andExpect(jsonPath("$[1].lastOpenedAt").doesNotExist());

        // Đổi tên trùng tên tài liệu khác trong cùng thư mục bị báo kèm gợi ý.
        mvc.perform(json(patch(LIBRARY + "/documents/" + ids.get(1)), learner, "{\"title\":\"đại SỐ\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.suggestion").value("đại SỐ (2)"));
        mvc.perform(json(patch(LIBRARY + "/documents/" + ids.get(1)), learner, "{\"title\":\"Anh văn  giao tiếp\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Anh văn giao tiếp"));

        // Xóa nhiều tài liệu cùng lúc: vào thùng rác cùng một đợt.
        mvc.perform(json(post(LIBRARY + "/documents/bulk"), learner, """
                        {"action":"TRASH","documentIds":["%s","%s"]}""".formatted(ids.get(1), ids.get(2))))
                .andExpect(jsonPath("$.trashBatchId").isNotEmpty());
        mvc.perform(get(LIBRARY + "/documents").cookie(learner.cookies())).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("Chuyển thư mục vào chính nó hay thư mục con của nó bị từ chối; chuyển hợp lệ đổi thư mục cha")
    void movesFoldersWithoutCycles() throws Exception {
        User learner = learner();
        String seeded = seed(learner, """
                {"folders":["Năm nhất/Học kỳ một/Giải tích","Lưu trữ"]}""");
        String year = JsonPath.read(seeded, "$.folders['Năm nhất']");
        String calculus = JsonPath.read(seeded, "$.folders['Năm nhất/Học kỳ một/Giải tích']");
        String archive = JsonPath.read(seeded, "$.folders['Lưu trữ']");

        mvc.perform(json(post(LIBRARY + "/folders/" + year + "/move"), learner, """
                        {"parentId":"%s"}""".formatted(calculus)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LIBRARY_FOLDER_CYCLE"));
        mvc.perform(json(post(LIBRARY + "/folders/" + year + "/move"), learner, """
                        {"parentId":"%s"}""".formatted(year)))
                .andExpect(status().isConflict());
        mvc.perform(json(post(LIBRARY + "/folders/" + year + "/move"), learner, """
                        {"parentId":"%s"}""".formatted(archive)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").value(archive));
        mvc.perform(json(post(LIBRARY + "/folders/" + year + "/move"), learner, "{\"parentId\":null}"))
                .andExpect(jsonPath("$.parentId").doesNotExist());
        mvc.perform(json(patch(LIBRARY + "/folders/" + calculus), learner, "{\"name\":\"Giải tích 1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Giải tích 1"));
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
        String email = "kho." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
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
