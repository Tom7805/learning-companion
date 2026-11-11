package com.learningcompanion.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.learningcompanion.shared.mail.MockMailGateway;
import com.learningcompanion.support.PostgresTestConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** NCL-06-CN-002 — dàn ý chủ đề của không gian học: đề xuất bằng trợ lý, tự lập, đề xuất lại, giới hạn ba cấp và chủ sở hữu. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class TopicOutlineIT {

    private static final String PASSWORD = "hoi-dap-co-can-cu-2026";
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMailGateway mailbox;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void fresh() throws Exception {
        mvc.perform(post("/api/v1/dev/quota/reset")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/dev/ai/reset?chunkDelayMs=0")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/dev/ai/configs/reset")).andExpect(status().isNoContent());
    }

    @AfterEach
    void restore() throws Exception {
        mvc.perform(post("/api/v1/dev/ai/reset")).andExpect(status().isNoContent());
    }

    private static final String CELL = """
            Màng tế bào bao quanh tế bào chất và điều khiển chất đi vào, đi ra.

            Ti thể là nơi diễn ra hô hấp tế bào, tạo năng lượng cho tế bào.

            Nhân tế bào chứa vật chất di truyền và điều khiển mọi hoạt động sống.""";

    @Test
    @DisplayName("TC-01: không gian học có ba nguồn sẵn sàng, đề xuất dàn ý ra cây hai cấp có mục tiêu học và trích dẫn")
    void suggestOutline() throws Exception {
        User learner = learner();
        String one = paste(learner, "Bài 1 Màng tế bào", CELL);
        String two = paste(learner, "Bài 2 Ti thể", CELL);
        String three = paste(learner, "Bài 3 Nhân tế bào", CELL);
        String space = workspace(learner, "Sinh học tế bào", one, two, three);
        long before = usedUnits(learner);

        JsonNode tree = body(mvc.perform(post(topics(space) + "/suggest").with(csrf()).cookie(learner.cookies()))
                .andExpect(status().isOk()).andReturn());
        assertThat(tree.get("added").asInt()).isGreaterThan(3);
        JsonNode roots = tree.get("topics");
        assertThat(roots).hasSize(3);
        for (JsonNode root : roots) {
            assertThat(root.get("aiGenerated").asBoolean()).isTrue();
            assertThat(root.get("goal").asString()).isNotBlank();
            assertThat(root.get("sources")).isNotEmpty();
            assertThat(root.get("sourceCount").asInt()).isEqualTo(1);
            JsonNode child = root.get("children").get(0);
            assertThat(child.get("goal").asString()).startsWith("Hiểu được");
            JsonNode source = child.get("sources").get(0);
            assertThat(source.get("location").get("kind").asString()).isEqualTo("PARAGRAPH");
            assertThat(source.get("passage").asInt()).isPositive();
            assertThat(source.get("available").asBoolean()).isTrue();
        }
        assertThat(usedUnits(learner) - before).isEqualTo(2);
    }

    @Test
    @DisplayName("TC-02: tự lập năm chủ đề không dùng trợ lý thì dàn ý được lưu và không tốn lượt")
    void manualOutlineCostsNothing() throws Exception {
        User learner = learner();
        String space = workspace(learner, "Tự học Lịch sử");
        long before = usedUnits(learner);

        JsonNode tree = null;
        for (int i = 1; i <= 5; i++) {
            tree = body(mvc.perform(json(post(topics(space)), learner, Map.of("title", "Chủ đề " + i,
                    "goal", "Mục tiêu " + i))).andExpect(status().isOk()).andReturn());
        }
        assertThat(tree.get("topics")).hasSize(5);
        assertThat(tree.get("topics").get(4).get("title").asString()).isEqualTo("Chủ đề 5");
        assertThat(tree.get("topics").get(0).get("aiGenerated").asBoolean()).isFalse();
        assertThat(usedUnits(learner)).isEqualTo(before);
    }

    @Test
    @DisplayName("TC-03: đã sửa tay tên hai chủ đề, đề xuất lại giữ nguyên hai chủ đề đó và bổ sung chủ đề mới")
    void resuggestKeepsEditedTopics() throws Exception {
        User learner = learner();
        String one = paste(learner, "Bài 1 Màng tế bào", CELL);
        String two = paste(learner, "Bài 2 Ti thể", CELL);
        String space = workspace(learner, "Sinh học tế bào", one, two);
        JsonNode first = body(mvc.perform(post(topics(space) + "/suggest").with(csrf()).cookie(learner.cookies()))
                .andExpect(status().isOk()).andReturn());
        String a = first.get("topics").get(0).get("id").asString();
        String b = first.get("topics").get(1).get("id").asString();
        mvc.perform(json(patch(topics(space) + "/" + a), learner, Map.of("title", "Màng sinh chất (tự sửa)")))
                .andExpect(status().isOk());
        mvc.perform(json(patch(topics(space) + "/" + b), learner, Map.of("title", "Năng lượng (tự sửa)")))
                .andExpect(status().isOk());

        JsonNode again = body(mvc.perform(post(topics(space) + "/suggest").with(csrf()).cookie(learner.cookies()))
                .andExpect(status().isOk()).andReturn());
        List<String> titles = new ArrayList<>();
        again.get("topics").forEach(node -> titles.add(node.get("title").asString()));
        assertThat(titles).contains("Màng sinh chất (tự sửa)", "Năng lượng (tự sửa)", "Bài 1 Màng tế bào",
                "Bài 2 Ti thể");
        JsonNode edited = again.get("topics").get(0);
        assertThat(edited.get("id").asString()).isEqualTo(a);
        assertThat(edited.get("aiGenerated").asBoolean()).isTrue();
        assertThat(edited.get("edited").asBoolean()).isTrue();
        assertThat(again.get("added").asInt()).isPositive();
    }

    @Test
    @DisplayName("TC-04: kéo một chủ đề xuống cấp thứ tư thì bị từ chối và chủ đề giữ nguyên chỗ cũ")
    void depthLimit() throws Exception {
        User learner = learner();
        String space = workspace(learner, "Ba cấp");
        String level1 = lastId(body(mvc.perform(json(post(topics(space)), learner, Map.of("title", "Cấp 1")))
                .andReturn()), null);
        String level2 = lastId(body(mvc.perform(json(post(topics(space)), learner,
                Map.of("title", "Cấp 2", "parentId", level1))).andReturn()), level1);
        JsonNode tree = body(mvc.perform(json(post(topics(space)), learner,
                Map.of("title", "Cấp 3", "parentId", level2))).andExpect(status().isOk()).andReturn());
        String level3 = tree.get("topics").get(0).get("children").get(0).get("children").get(0).get("id").asString();
        String loose = lastId(body(mvc.perform(json(post(topics(space)), learner, Map.of("title", "Chủ đề rời")))
                .andReturn()), null);

        mvc.perform(json(put(topics(space) + "/" + loose + "/position"), learner,
                        Map.of("parentId", level3, "index", 0)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TOPIC_DEPTH_EXCEEDED"));
        mvc.perform(json(post(topics(space)), learner, Map.of("title", "Cấp 4", "parentId", level3)))
                .andExpect(status().isUnprocessableContent());
        JsonNode after = body(mvc.perform(get(topics(space)).cookie(learner.cookies())).andReturn());
        assertThat(after.get("topics")).hasSize(2);
        assertThat(after.get("topics").get(1).get("id").asString()).isEqualTo(loose);

        // Đưa cấp 2 (cùng cấp 3 bên dưới) lên làm chủ đề gốc thì được.
        JsonNode moved = body(mvc.perform(json(put(topics(space) + "/" + level2 + "/position"), learner,
                Map.of("index", 0))).andExpect(status().isOk()).andReturn());
        assertThat(moved.get("topics").get(0).get("id").asString()).isEqualTo(level2);
        assertThat(moved.get("topics").get(0).get("children").get(0).get("id").asString()).isEqualTo(level3);
    }

    @Test
    @DisplayName("gộp và tách chủ đề: con và nguồn chuyển sang, chủ đề mới cùng cấp nằm ngay sau")
    void mergeAndSplit() throws Exception {
        User learner = learner();
        String space = workspace(learner, "Gộp tách");
        String a = lastId(body(mvc.perform(json(post(topics(space)), learner, Map.of("title", "A", "goal", "Mục A")))
                .andReturn()), null);
        String b = lastId(body(mvc.perform(json(post(topics(space)), learner, Map.of("title", "B", "goal", "Mục B")))
                .andReturn()), null);
        mvc.perform(json(post(topics(space)), learner, Map.of("title", "B1", "parentId", b))).andReturn();
        JsonNode merged = body(mvc.perform(json(post(topics(space) + "/" + b + "/merge"), learner,
                Map.of("intoId", a))).andExpect(status().isOk()).andReturn());
        assertThat(merged.get("topics")).hasSize(1);
        assertThat(merged.get("topics").get(0).get("goal").asString()).isEqualTo("Mục A Mục B");
        String b1 = merged.get("topics").get(0).get("children").get(0).get("id").asString();

        JsonNode split = body(mvc.perform(json(post(topics(space) + "/" + a + "/split"), learner,
                Map.of("title", "A phần hai", "childIds", List.of(b1)))).andExpect(status().isOk()).andReturn());
        assertThat(split.get("topics")).hasSize(2);
        assertThat(split.get("topics").get(1).get("title").asString()).isEqualTo("A phần hai");
        assertThat(split.get("topics").get(1).get("children").get(0).get("id").asString()).isEqualTo(b1);
        assertThat(split.get("topics").get(0).get("children")).isEmpty();
    }

    @Test
    @DisplayName("TC-05: người học khác sửa dàn ý của không gian học bằng mã thì nhận không tìm thấy và bị ghi nhật ký")
    void strangerCannotEditOutline() throws Exception {
        User owner = learner();
        User stranger = learner();
        String space = workspace(owner, "Của riêng tôi");
        String topic = lastId(body(mvc.perform(json(post(topics(space)), owner, Map.of("title", "Riêng")))
                .andReturn()), null);

        mvc.perform(json(post(topics(space)), stranger, Map.of("title", "Chen vào")))
                .andExpect(status().isNotFound());
        mvc.perform(json(patch(topics(space) + "/" + topic), stranger, Map.of("title", "Đổi")))
                .andExpect(status().isNotFound());
        mvc.perform(get(topics(space)).cookie(stranger.cookies())).andExpect(status().isNotFound());
        assertThat(count("select count(*) from audit_log where action = 'LEARNING_ACCESS_DENIED' "
                + "and actor_id = ?::uuid and object_type = 'WORKSPACE'", stranger.id())).isGreaterThanOrEqualTo(3);
        JsonNode mine = body(mvc.perform(get(topics(space)).cookie(owner.cookies())).andReturn());
        assertThat(mine.get("topics").get(0).get("title").asString()).isEqualTo("Riêng");
    }

    private static String topics(String workspace) {
        return "/api/v1/workspaces/" + workspace + "/topics";
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Mã chủ đề vừa thêm: chủ đề cuối cùng dưới {@code parentId} (trống là cấp gốc). */
    private static String lastId(JsonNode tree, String parentId) {
        JsonNode list = tree.get("topics");
        if (parentId != null) {
            list = find(list, parentId).get("children");
        }
        return list.get(list.size() - 1).get("id").asString();
    }

    private static JsonNode find(JsonNode list, String id) {
        for (JsonNode node : list) {
            if (node.get("id").asString().equals(id)) {
                return node;
            }
            JsonNode inner = find(node.get("children"), id);
            if (inner != null) {
                return inner;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- hỗ trợ

    private String paste(User user, String title, String text) throws Exception {
        // Nội dung khác nhau theo tên để không bị báo trùng tài liệu.
        String body = mvc.perform(json(post("/api/v1/library/sources/text"), user,
                        Map.of("title", title, "text", title + ". " + text)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        // Chờ tài liệu được xử lý xong (đoạn đã lập chỉ mục) trước khi hỏi.
        await().atMost(Duration.ofSeconds(60)).until(() -> "READY".equals(jdbc.queryForObject(
                "select processing_status from documents where id = ?::uuid", String.class, id)));
        return id;
    }

    private String workspace(User user, String name, String... documents) throws Exception {
        String id = JsonPath.read(mvc.perform(json(post("/api/v1/workspaces"), user, Map.of("name", name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        if (documents.length > 0) {
            mvc.perform(json(post("/api/v1/workspaces/" + id + "/documents"), user,
                    Map.of("documentIds", List.of(documents)))).andExpect(status().isOk());
        }
        return id;
    }

    private long usedUnits(User user) throws Exception {
        String body = mvc.perform(get("/api/v1/quota").cookie(user.cookies())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Number used = JsonPath.read(body, "$.ai.usedUnits");
        Number reserved = JsonPath.read(body, "$.ai.reservedUnits");
        return used.longValue() + reserved.longValue();
    }

    private int count(String sql, Object... args) {
        Integer found = jdbc.queryForObject(sql, Integer.class, args);
        return found == null ? 0 : found;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder json(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, User user,
            Map<String, Object> body) throws Exception {
        return request.with(csrf()).cookie(user.cookies()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LinkedHashMap<>(body)));
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
        String email = "hoidap." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
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
