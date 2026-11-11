package com.learningcompanion.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** NCL-07-CN-002 — trích dẫn bấm mở đúng đoạn: xem nhanh đoạn gốc, tài liệu không còn, kiểm tra chủ sở hữu. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class CitationIT {

    private static final String PASSWORD = "hoi-dap-co-can-cu-2026";
    private static final String TREE = """
            Cây nhị phân là cấu trúc dữ liệu mà mỗi nút có tối đa hai con.

            Duyệt cây theo thứ tự giữa thăm cây con trái, rồi tới nút gốc, rồi tới cây con phải. Với cây nhị phân \
            tìm kiếm, duyệt theo thứ tự giữa cho ra các khóa theo thứ tự tăng dần.

            Duyệt theo thứ tự trước thăm nút gốc trước rồi mới tới hai cây con.""";
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

    @Test
    @DisplayName("TC-01: trích dẫn trỏ đúng trang tám của bài giảng, thứ tự đoạn khớp khung xem và có phần cần tô sáng")
    void citationPointsToPageAndPassage() throws Exception {
        User learner = learner();
        String lecture = paste(learner, "Bài giảng cây nhị phân", TREE);
        // Bài giảng dạng trang: đoạn nói về thứ tự giữa là đoạn thứ hai của trang tám.
        jdbc.update("delete from document_chunks where document_id = ?::uuid", lecture);
        String insert = "insert into document_chunks (id, document_id, owner_id, ordinal, location_kind, "
                + "location_number, location_end, content, source, embedding, created_at) "
                + "values (gen_random_uuid(), ?::uuid, ?::uuid, ?, 'PAGE', 8, 8, ?, 'TEXT', '{0.1}', now())";
        jdbc.update(insert, lecture, learner.id(), 1, "Trang tám mở đầu bằng phần giới thiệu chung.");
        jdbc.update(insert, lecture, learner.id(), 2, TREE);

        JsonNode done = ask(learner, Map.of("question", "Cách duyệt cây theo thứ tự giữa là gì?",
                "documentIds", List.of(lecture))).done();
        JsonNode citation = done.get("citations").get(0);
        assertThat(citation.get("location").get("kind").asString()).isEqualTo("PAGE");
        assertThat(citation.get("location").get("number").asInt()).isEqualTo(8);
        assertThat(citation.get("passage").asInt()).isEqualTo(2);
        assertThat(citation.get("available").asBoolean()).isTrue();
        String excerpt = citation.get("excerpt").asString();
        JsonNode highlight = citation.get("highlight");
        assertThat(excerpt.substring(highlight.get("start").asInt(), highlight.get("end").asInt()))
                .contains("thứ tự giữa");

        String id = citation.get("id").asString();
        mvc.perform(get("/api/v1/assistant/citations/" + id).cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(lecture))
                .andExpect(jsonPath("$.documentTitle").value("Bài giảng cây nhị phân"))
                .andExpect(jsonPath("$.location.number").value(8))
                .andExpect(jsonPath("$.passage").value(2))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.highlight.start").value(highlight.get("start").asInt()));
    }

    @Test
    @DisplayName("TC-03: tài liệu được trích đã bị xóa hẳn (hay vào thùng rác) thì trích dẫn báo tài liệu không còn")
    void citationOfDeletedDocument() throws Exception {
        User learner = learner();
        String lecture = paste(learner, "Bài giảng cây nhị phân", TREE);
        Stream stream = ask(learner, Map.of("question", "Cách duyệt cây theo thứ tự giữa là gì?",
                "documentIds", List.of(lecture)));
        String id = stream.done().get("citations").get(0).get("id").asString();
        String conversation = stream.meta().get("conversationId").asString();

        mvc.perform(delete("/api/v1/library/documents/" + lecture).with(csrf()).cookie(learner.cookies()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/assistant/citations/" + id).cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.documentId").value(lecture));

        // Xóa hẳn: dòng tài liệu biến mất, trích dẫn vẫn giữ tên, vị trí và đoạn gốc.
        jdbc.update("delete from documents where id = ?::uuid", lecture);
        mvc.perform(get("/api/v1/assistant/citations/" + id).cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.documentId").doesNotExist())
                .andExpect(jsonPath("$.documentTitle").value("Bài giảng cây nhị phân"))
                .andExpect(jsonPath("$.excerpt").isNotEmpty());
        mvc.perform(get("/api/v1/assistant/conversations/" + conversation).cookie(learner.cookies()))
                .andExpect(jsonPath("$.messages[1].citations[0].available").value(false))
                .andExpect(jsonPath("$.messages[1].citations[0].documentTitle").value("Bài giảng cây nhị phân"));
    }

    @Test
    @DisplayName("TC-04: người học khác gọi điểm cuối lấy đoạn gốc của trích dẫn bằng mã: 404 và ghi nhật ký")
    void strangerCannotReadCitation() throws Exception {
        User owner = learner();
        User stranger = learner();
        String lecture = paste(owner, "Bài giảng cây nhị phân", TREE);
        String id = ask(owner, Map.of("question", "Cách duyệt cây theo thứ tự giữa là gì?",
                "documentIds", List.of(lecture))).done().get("citations").get(0).get("id").asString();

        mvc.perform(get("/api/v1/assistant/citations/" + id).cookie(stranger.cookies()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.excerpt").doesNotExist());
        assertThat(count("select count(*) from audit_log where action = 'LEARNING_ACCESS_DENIED' and actor_id = ?::uuid "
                + "and object_type = 'CITATION' and object_id = ?::uuid", stranger.id(), id)).isEqualTo(1);
        mvc.perform(get("/api/v1/assistant/citations/" + UUID.randomUUID()).cookie(stranger.cookies()))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- hỗ trợ

    private Stream ask(User user, Map<String, Object> body) throws Exception {
        return stream(mvc.perform(json(post("/api/v1/assistant/ask"), user, body)).andExpect(status().isOk())
                .andReturn());
    }

    private Stream stream(MvcResult started) throws Exception {
        await().atMost(Duration.ofSeconds(20)).until(() -> {
            String content = started.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            return content.contains("event:done") || content.contains("event:error");
        });
        return Stream.parse(json, started.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private record Stream(JsonNode meta, List<String> chunks, JsonNode done, JsonNode error) {

        static Stream parse(ObjectMapper json, String body) throws Exception {
            JsonNode meta = null;
            JsonNode done = null;
            JsonNode error = null;
            List<String> chunks = new ArrayList<>();
            for (String block : body.replace("\r\n", "\n").split("\n\n")) {
                String name = null;
                StringBuilder data = new StringBuilder();
                for (String line : block.split("\n")) {
                    if (line.startsWith("event:")) {
                        name = line.substring(6).strip();
                    } else if (line.startsWith("data:")) {
                        data.append(line.substring(5));
                    }
                }
                if (name == null) {
                    continue;
                }
                JsonNode node = json.readTree(data.toString());
                switch (name) {
                    case "meta" -> meta = node;
                    case "chunk" -> chunks.add(node.get("text").asString());
                    case "done" -> done = node;
                    case "error" -> error = node;
                    default -> {
                    }
                }
            }
            return new Stream(meta, chunks, done, error);
        }
    }

    private String paste(User user, String title, String text) throws Exception {
        String body = mvc.perform(json(post("/api/v1/library/sources/text"), user,
                        Map.of("title", title, "text", text)))
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
        mvc.perform(json(post("/api/v1/workspaces/" + id + "/documents"), user,
                Map.of("documentIds", List.of(documents)))).andExpect(status().isOk());
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
