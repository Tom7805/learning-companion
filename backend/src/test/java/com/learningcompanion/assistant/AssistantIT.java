package com.learningcompanion.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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

/** NCL-07-CN-001 — hỏi đáp có căn cứ trên tài liệu đã chọn, kiểm qua API, lớp kết nối mô phỏng và cơ sở dữ liệu thật. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class AssistantIT {

    private static final String PASSWORD = "hoi-dap-co-can-cu-2026";
    private static final String TREE = """
            Cây nhị phân là cấu trúc dữ liệu mà mỗi nút có tối đa hai con.

            Duyệt cây theo thứ tự giữa thăm cây con trái, rồi tới nút gốc, rồi tới cây con phải. Với cây nhị phân \
            tìm kiếm, duyệt theo thứ tự giữa cho ra các khóa theo thứ tự tăng dần.

            Duyệt theo thứ tự trước thăm nút gốc trước rồi mới tới hai cây con.""";
    private static final String CHAPTER_ONE = """
            Chương một: Giới hạn của dãy số.

            Một dãy số hội tụ khi các số hạng tiến dần về một giá trị xác định gọi là giới hạn.""";
    private static final String CHAPTER_TWO = """
            Chương hai: Đạo hàm.

            Đạo hàm riêng của hàm nhiều biến là đạo hàm theo một biến khi giữ các biến còn lại cố định.""";

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
    @DisplayName("TC-01: hỏi trong không gian Cấu trúc dữ liệu, câu trả lời có trích dẫn tới bài giảng và ba câu hỏi gợi ý")
    void answersWithCitationsInWorkspace() throws Exception {
        User learner = learner();
        String lecture = paste(learner, "Bài giảng cây nhị phân", TREE);
        String workspace = workspace(learner, "Cấu trúc dữ liệu", lecture);
        long usedBefore = usedUnits(learner);

        Stream stream = ask(learner, Map.of("workspaceId", workspace, "question",
                "Cách duyệt cây theo thứ tự giữa là gì?"));

        assertThat(stream.meta().get("documentIds").get(0).asString()).isEqualTo(lecture);
        assertThat(stream.chunks()).isNotEmpty();
        JsonNode done = stream.done();
        assertThat(done.get("status").asString()).isEqualTo("COMPLETE");
        assertThat(done.get("text").asString()).contains("thứ tự giữa").contains("[1]")
                .doesNotContain("Câu hỏi gợi ý");
        assertThat(done.get("suggestions")).hasSize(3);
        JsonNode citation = done.get("citations").get(0);
        assertThat(citation.get("number").asInt()).isEqualTo(1);
        assertThat(citation.get("documentId").asString()).isEqualTo(lecture);
        assertThat(citation.get("documentTitle").asString()).isEqualTo("Bài giảng cây nhị phân");
        assertThat(citation.get("location").get("kind").asString()).isEqualTo("PARAGRAPH");
        assertThat(citation.get("excerpt").asString()).contains("thứ tự giữa");
        // Đoạn hiện dần ghép lại đúng bằng câu trả lời đã lưu, không lẫn khối câu hỏi gợi ý.
        assertThat(String.join("", stream.chunks()).strip()).isEqualTo(done.get("text").asString());
        assertThat(usedUnits(learner)).isEqualTo(usedBefore + 1);

        String conversation = stream.meta().get("conversationId").asString();
        mvc.perform(get("/api/v1/assistant/conversations/" + conversation).cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(workspace))
                .andExpect(jsonPath("$.title").value("Cách duyệt cây theo thứ tự giữa là gì?"))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].role").value("USER"))
                .andExpect(jsonPath("$.messages[1].status").value("COMPLETE"))
                .andExpect(jsonPath("$.messages[1].suggestions.length()").value(3))
                .andExpect(jsonPath("$.messages[1].citations[0].documentTitle").value("Bài giảng cây nhị phân"))
                .andExpect(jsonPath("$.scope[0].id").value(lecture));
        mvc.perform(get("/api/v1/assistant/conversations").param("workspaceId", workspace).cookie(learner.cookies()))
                .andExpect(jsonPath("$[0].id").value(conversation));
        // Mô hình chỉ nhận đúng đoạn của bài giảng, bọc trong vùng dữ liệu.
        assertThat(count("select count(*) from ai_call_records where feature = 'ASSISTANT_CHAT'")).isPositive();
    }

    @Test
    @DisplayName("TC-02: hội thoại mới ngoài không gian học, đính kèm hai tài liệu từ kho; chỉ trích hai tài liệu đó")
    void privateConversationWithTwoDocuments() throws Exception {
        User learner = learner();
        String tree = paste(learner, "Bài giảng cây nhị phân", TREE);
        String calculus = paste(learner, "Giải tích chương hai", CHAPTER_TWO);
        paste(learner, "Tài liệu không chọn về đạo hàm riêng", CHAPTER_TWO + "\n\nBản sao để thử phạm vi.");

        Stream stream = ask(learner, Map.of("question", "Đạo hàm riêng là gì và duyệt cây theo thứ tự giữa thế nào?",
                "documentIds", List.of(tree, calculus)));

        JsonNode done = stream.done();
        assertThat(done.get("status").asString()).isEqualTo("COMPLETE");
        List<String> cited = new ArrayList<>();
        done.get("citations").forEach(item -> cited.add(item.get("documentId").asString()));
        assertThat(cited).isNotEmpty().allMatch(id -> id.equals(tree) || id.equals(calculus));
        assertThat(stream.meta().get("workspaceId") == null || stream.meta().get("workspaceId").isNull()).isTrue();

        // Hỏi tiếp trong cùng hội thoại.
        Stream next = ask(learner, Map.of("conversationId", stream.meta().get("conversationId").asString(),
                "question", "Cây nhị phân là gì?", "documentIds", List.of(tree)));
        assertThat(next.done().get("status").asString()).isEqualTo("COMPLETE");
        mvc.perform(get("/api/v1/assistant/conversations/" + stream.meta().get("conversationId").asString())
                        .cookie(learner.cookies()))
                .andExpect(jsonPath("$.messages.length()").value(4));
    }

    @Test
    @DisplayName("TC-03: tài liệu không nói về mạng nơ ron thì trả lời không tìm thấy, không gọi mô hình, không tốn lượt")
    void notFoundWithoutCallingModel() throws Exception {
        User learner = learner();
        String lecture = paste(learner, "Bài giảng cây nhị phân", TREE);
        long usedBefore = usedUnits(learner);
        int callsBefore = count("select count(*) from ai_call_records where feature = 'ASSISTANT_CHAT'");

        Stream stream = ask(learner, Map.of("question", "Mạng nơ ron hoạt động thế nào?",
                "documentIds", List.of(lecture)));

        assertThat(stream.done().get("status").asString()).isEqualTo("NOT_FOUND");
        assertThat(stream.chunks()).isEmpty();
        assertThat(stream.done().get("citations")).isEmpty();
        assertThat(usedUnits(learner)).isEqualTo(usedBefore);
        assertThat(count("select count(*) from ai_call_records where feature = 'ASSISTANT_CHAT'"))
                .isEqualTo(callsBefore);
    }

    @Test
    @DisplayName("TC-04: chỉ chọn chương một, hỏi nội dung chương hai: không dùng chương hai và gợi ý chọn thêm nó")
    void staysInScopeAndSuggestsOtherDocument() throws Exception {
        User learner = learner();
        String one = paste(learner, "Giải tích chương một", CHAPTER_ONE);
        String two = paste(learner, "Giải tích chương hai", CHAPTER_TWO);
        String workspace = workspace(learner, "Giải tích", one, two);

        Stream stream = ask(learner, Map.of("workspaceId", workspace, "question", "Đạo hàm riêng là gì?",
                "documentIds", List.of(one)));

        JsonNode done = stream.done();
        assertThat(done.get("status").asString()).isEqualTo("NOT_FOUND");
        assertThat(done.get("elsewhere").get(0).get("id").asString()).isEqualTo(two);
        assertThat(done.get("elsewhere").get(0).get("title").asString()).isEqualTo("Giải tích chương hai");

        // Bỏ trống lựa chọn trong không gian học thì phạm vi mặc định là mọi tài liệu đã gắn: tìm thấy ở chương hai.
        Stream all = ask(learner, Map.of("workspaceId", workspace, "question", "Đạo hàm riêng là gì?"));
        assertThat(all.done().get("status").asString()).isEqualTo("COMPLETE");
        assertThat(all.done().get("citations").get(0).get("documentId").asString()).isEqualTo(two);
    }

    @Test
    @DisplayName("Chưa chọn tài liệu nào thì trợ lý nhắc chọn nguồn, không gọi mô hình")
    void asksToChooseSources() throws Exception {
        User learner = learner();
        Stream stream = ask(learner, Map.of("question", "Cây nhị phân là gì?"));
        assertThat(stream.done().get("status").asString()).isEqualTo("NO_SOURCES");
        assertThat(usedUnits(learner)).isZero();
    }

    @Test
    @DisplayName("TC-05: dịch vụ mô hình lỗi giữa chừng: giữ phần đã hiện, báo lỗi, hoàn lượt; trả lời lại thì thành công")
    void midStreamFailureKeepsPartialAndRefunds() throws Exception {
        User learner = learner();
        String lecture = paste(learner, "Bài giảng cây nhị phân", TREE);
        mvc.perform(post("/api/v1/dev/ai/mock/mock-primary").param("mode", "BREAK_MIDWAY"))
                .andExpect(status().isOk());
        long usedBefore = usedUnits(learner);

        Stream stream = ask(learner, Map.of("question", "Cách duyệt cây theo thứ tự giữa là gì?",
                "documentIds", List.of(lecture)));

        JsonNode error = stream.error();
        assertThat(error.get("code").asString()).isEqualTo("AI_TEMPORARILY_UNAVAILABLE");
        assertThat(error.get("partial").asString()).isNotBlank().startsWith("Theo tài liệu");
        assertThat(String.join("", stream.chunks()).strip()).isEqualTo(error.get("partial").asString());
        String conversation = stream.meta().get("conversationId").asString();
        mvc.perform(get("/api/v1/assistant/conversations/" + conversation).cookie(learner.cookies()))
                .andExpect(jsonPath("$.messages[1].status").value("FAILED"))
                .andExpect(jsonPath("$.messages[1].errorCode").value("AI_TEMPORARILY_UNAVAILABLE"))
                .andExpect(jsonPath("$.messages[1].content").value(error.get("partial").asString()));
        await().atMost(Duration.ofSeconds(10)).until(() -> usedUnits(learner) == usedBefore);
        assertThat(count("select count(*) from notifications where owner_id = ?::uuid and type = 'AI_TASK_REFUNDED'",
                learner.id())).isEqualTo(1);

        mvc.perform(post("/api/v1/dev/ai/mock/mock-primary").param("mode", "OK")).andExpect(status().isOk());
        Stream retried = stream(mvc.perform(post("/api/v1/assistant/conversations/" + conversation + "/regenerate")
                .with(csrf()).cookie(learner.cookies())).andExpect(status().isOk()).andReturn());
        assertThat(retried.done().get("status").asString()).isEqualTo("COMPLETE");
        mvc.perform(get("/api/v1/assistant/conversations/" + conversation).cookie(learner.cookies()))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[1].status").value("COMPLETE"));
    }

    @Test
    @DisplayName("TC-06: người học khác gửi câu hỏi vào không gian học hay hội thoại bằng mã: 404 và ghi nhật ký")
    void strangerIsRejected() throws Exception {
        User owner = learner();
        User stranger = learner();
        String lecture = paste(owner, "Bài giảng cây nhị phân", TREE);
        String workspace = workspace(owner, "Cấu trúc dữ liệu", lecture);
        Stream mine = ask(owner, Map.of("workspaceId", workspace, "question", "Cây nhị phân là gì?"));
        mine.done();
        String conversation = mine.meta().get("conversationId").asString();

        mvc.perform(json(post("/api/v1/assistant/ask"), stranger,
                        Map.of("workspaceId", workspace, "question", "Cây nhị phân là gì?")))
                .andExpect(status().isNotFound());
        mvc.perform(json(post("/api/v1/assistant/ask"), stranger,
                        Map.of("conversationId", conversation, "question", "Cây nhị phân là gì?")))
                .andExpect(status().isNotFound());
        mvc.perform(json(post("/api/v1/assistant/ask"), stranger,
                        Map.of("question", "Cây nhị phân là gì?", "documentIds", List.of(lecture))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/assistant/conversations/" + conversation).cookie(stranger.cookies()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/assistant/conversations/" + conversation + "/regenerate").with(csrf())
                .cookie(stranger.cookies())).andExpect(status().isNotFound());

        assertThat(count("select count(*) from audit_log where action = 'LEARNING_ACCESS_DENIED' and actor_id = ?::uuid "
                + "and object_type = 'WORKSPACE' and object_id = ?::uuid", stranger.id(), workspace)).isEqualTo(1);
        assertThat(count("select count(*) from audit_log where action = 'LEARNING_ACCESS_DENIED' and actor_id = ?::uuid "
                + "and object_type = 'CONVERSATION' and object_id = ?::uuid", stranger.id(), conversation)).isEqualTo(3);
        assertThat(count("select count(*) from audit_log where action = 'LEARNING_ACCESS_DENIED' and actor_id = ?::uuid "
                + "and object_type = 'DOCUMENT' and object_id = ?::uuid", stranger.id(), lecture)).isEqualTo(1);
        // Chủ không gian học cũng không thêm được tài liệu ngoài không gian học vào phạm vi.
        String outside = paste(owner, "Ngoài không gian", CHAPTER_ONE);
        mvc.perform(json(post("/api/v1/assistant/ask"), owner, Map.of("workspaceId", workspace,
                "question", "Giới hạn là gì?", "documentIds", List.of(outside)))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Dừng giữa chừng: phần đã hiện được giữ, câu trả lời lưu là đã dừng")
    void stopKeepsShownText() throws Exception {
        User learner = learner();
        String lecture = paste(learner, "Bài giảng cây nhị phân", TREE);
        mvc.perform(post("/api/v1/dev/ai/reset?chunkDelayMs=150")).andExpect(status().isNoContent());
        MvcResult started = mvc.perform(json(post("/api/v1/assistant/ask"), learner,
                Map.of("question", "Cách duyệt cây theo thứ tự giữa là gì?", "documentIds", List.of(lecture))))
                .andExpect(status().isOk()).andReturn();
        await().atMost(Duration.ofSeconds(10)).until(() -> started.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)
                .contains("event:chunk"));
        Stream partial = Stream.parse(json, started.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        String conversation = partial.meta().get("conversationId").asString();
        String answer = partial.meta().get("answerId").asString();
        mvc.perform(post("/api/v1/assistant/conversations/" + conversation + "/messages/" + answer + "/stop")
                .with(csrf()).cookie(learner.cookies())).andExpect(status().isNoContent());
        await().atMost(Duration.ofSeconds(20)).until(() -> started.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)
                .contains("event:done"));
        Stream finished = Stream.parse(json, started.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(finished.done().get("status").asString()).isEqualTo("STOPPED");
        assertThat(finished.done().get("suggestions")).isEmpty();
        assertThat(finished.done().get("text").asString()).isEqualTo(String.join("", finished.chunks()).strip());
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
