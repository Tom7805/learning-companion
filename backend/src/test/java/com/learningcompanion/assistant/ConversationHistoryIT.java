package com.learningcompanion.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.learningcompanion.ai.gateway.model.ChatRequest;
import com.learningcompanion.ai.provider.mock.MockAiAdapter;
import com.learningcompanion.shared.mail.MockMailGateway;
import com.learningcompanion.support.PostgresTestConfiguration;
import jakarta.servlet.http.Cookie;
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

/**
 * NCL-07-CN-003 — lịch sử hội thoại ở thanh bên: ghim và sắp xếp, hỏi nối tiếp dùng các lượt trước, tìm theo tên và
 * nội dung, chuyển vào không gian học, đổi tên, xóa, tải về và chặn người lạ.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class ConversationHistoryIT {

    private static final String PASSWORD = "lich-su-hoi-thoai-2026";
    private static final String CONVERSATIONS = "/api/v1/assistant/conversations";
    private static final String TREE = """
            Cây nhị phân là cấu trúc dữ liệu mà mỗi nút có tối đa hai con.

            Duyệt cây theo thứ tự giữa thăm cây con trái, rồi tới nút gốc, rồi tới cây con phải. Với cây nhị phân \
            tìm kiếm, duyệt theo thứ tự giữa cho ra các khóa theo thứ tự tăng dần.

            Duyệt theo thứ tự trước thăm nút gốc trước rồi mới tới hai cây con.""";
    private static final String GRAPH = """
            Đồ thị gồm các đỉnh và các cạnh nối giữa các đỉnh.

            Tìm kiếm theo chiều rộng thăm các đỉnh theo từng lớp, dùng hàng đợi.""";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMailGateway mailbox;

    @Autowired
    private MockAiAdapter mock;

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
    @DisplayName("TC-01: lịch sử hội thoại riêng: đã ghim lên đầu, còn lại mới dùng trước; hội thoại không gian học không lẫn vào")
    void pinnedFirstThenMostRecent() throws Exception {
        User learner = learner();
        String old = start(learner, "Ôn tập đồ thị tuần trước");
        String yesterday = start(learner, "Hỏi về cây nhị phân hôm qua");
        String today = start(learner, "Câu hỏi hôm nay");
        jdbc.update("update assistant_conversations set updated_at = now() - interval '10 days' where id = ?::uuid", old);
        jdbc.update("update assistant_conversations set updated_at = now() - interval '1 day' where id = ?::uuid",
                yesterday);
        String lecture = paste(learner, "Bài giảng cây nhị phân", TREE);
        workspace(learner, "Cấu trúc dữ liệu", lecture);

        mvc.perform(patch(CONVERSATIONS + "/" + old).with(csrf()).cookie(learner.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pinned\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned").value(true));

        JsonNode list = list(learner, null);
        assertThat(ids(list)).containsExactly(old, today, yesterday);
        assertThat(list.get(0).get("pinned").asBoolean()).isTrue();
        assertThat(list.get(1).get("pinned").asBoolean()).isFalse();

        mvc.perform(patch(CONVERSATIONS + "/" + old).with(csrf()).cookie(learner.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pinned\":false}"))
                .andExpect(jsonPath("$.pinned").value(false));
        assertThat(ids(list(learner, null))).containsExactly(today, yesterday, old);
    }

    @Test
    @DisplayName("TC-02: mở lại hội thoại cũ và hỏi nối tiếp: mô hình nhận các lượt trước, số trích dẫn cũ được bỏ")
    void followUpUsesEarlierTurns() throws Exception {
        User learner = learner();
        String lecture = paste(learner, "Bài giảng cây nhị phân", TREE);
        Stream first = ask(learner, Map.of("question", "Cách duyệt cây theo thứ tự giữa là gì?",
                "documentIds", List.of(lecture)));
        assertThat(first.done().get("status").asString()).isEqualTo("COMPLETE");
        assertThat(mock.lastRequest().messages()).hasSize(1);
        String conversation = first.meta().get("conversationId").asString();

        Stream follow = ask(learner, Map.of("conversationId", conversation,
                "question", "Còn duyệt theo thứ tự trước thì sao?", "documentIds", List.of(lecture)));

        assertThat(follow.done().get("status").asString()).isEqualTo("COMPLETE");
        List<ChatRequest.Message> seen = mock.lastRequest().messages();
        assertThat(seen).hasSize(3);
        assertThat(seen.get(0).role()).isEqualTo(ChatRequest.Role.USER);
        assertThat(seen.get(0).text()).isEqualTo("Cách duyệt cây theo thứ tự giữa là gì?");
        assertThat(seen.get(1).role()).isEqualTo(ChatRequest.Role.ASSISTANT);
        assertThat(seen.get(1).text()).contains("thứ tự giữa").doesNotContain("[1]");
        assertThat(seen.get(2).text()).contains("Câu hỏi: Còn duyệt theo thứ tự trước thì sao?");
        assertThat(follow.done().get("text").asString())
                .startsWith("Nối tiếp câu hỏi trước (“Cách duyệt cây theo thứ tự giữa là gì?”)");

        // Câu hỏi nối tiếp thiếu chủ đề vẫn tìm được đoạn nhờ ghép với câu hỏi trước.
        Stream vague = ask(learner, Map.of("conversationId", conversation, "question", "Cho thêm ví dụ được không?",
                "documentIds", List.of(lecture)));
        assertThat(vague.done().get("status").asString()).isEqualTo("COMPLETE");
        assertThat(mock.lastRequest().messages()).hasSize(5);
    }

    @Test
    @DisplayName("TC-03: tìm hội thoại theo từ khóa trong tên và trong nội dung, không phân biệt hoa thường")
    void searchesTitleAndContent() throws Exception {
        User learner = learner();
        String lecture = paste(learner, "Bài giảng đồ thị", GRAPH);
        String byTitle = start(learner, "Ôn tập Đồ Thị cho kỳ thi");
        Stream grounded = ask(learner, Map.of("question", "Tìm kiếm theo chiều rộng dùng gì?",
                "documentIds", List.of(lecture)));
        grounded.done();
        String byContent = grounded.meta().get("conversationId").asString();
        start(learner, "Câu hỏi về lịch sử");

        assertThat(ids(list(learner, "đồ thị"))).containsExactly(byTitle);
        // "hàng đợi" chỉ có trong câu trả lời.
        assertThat(ids(list(learner, "HÀNG ĐỢI"))).containsExactly(byContent);
        assertThat(ids(list(learner, "  "))).hasSize(3);
        // Ký tự đại diện được hiểu đúng nghĩa đen.
        assertThat(ids(list(learner, "%"))).isEmpty();
        assertThat(ids(list(learner, "không có ở đâu"))).isEmpty();
    }

    @Test
    @DisplayName("TC-04: chuyển hội thoại riêng vào không gian học thì lượt hỏi sau dùng phạm vi tài liệu của không gian học")
    void movedConversationUsesWorkspaceScope() throws Exception {
        User learner = learner();
        String tree = paste(learner, "Bài giảng cây nhị phân", TREE);
        String graph = paste(learner, "Bài giảng đồ thị", GRAPH);
        String workspace = workspace(learner, "Cấu trúc dữ liệu", tree, graph);
        Stream first = ask(learner, Map.of("question", "Cây nhị phân là gì?", "documentIds", List.of(tree)));
        first.done();
        String conversation = first.meta().get("conversationId").asString();

        mvc.perform(put(CONVERSATIONS + "/" + conversation + "/workspace").with(csrf()).cookie(learner.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"workspaceId\":\"" + workspace + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(workspace));
        assertThat(ids(list(learner, null))).doesNotContain(conversation);
        assertThat(ids(list(learner, workspace, null))).containsExactly(conversation);

        Stream next = ask(learner, Map.of("conversationId", conversation,
                "question", "Tìm kiếm theo chiều rộng dùng gì?"));
        assertThat(next.meta().get("documentIds")).extracting(JsonNode::asString)
                .containsExactlyInAnyOrder(tree, graph);
        assertThat(next.done().get("citations").get(0).get("documentId").asString()).isEqualTo(graph);

        // Không chuyển được vào không gian học của người khác.
        User stranger = learner();
        String foreign = workspace(stranger, "Của người khác");
        mvc.perform(put(CONVERSATIONS + "/" + conversation + "/workspace").with(csrf()).cookie(learner.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"workspaceId\":\"" + foreign + "\"}"))
                .andExpect(status().isNotFound());
        // Đưa ra lại thành hội thoại riêng.
        mvc.perform(put(CONVERSATIONS + "/" + conversation + "/workspace").with(csrf()).cookie(learner.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"workspaceId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").doesNotExist());
        assertThat(ids(list(learner, null))).containsExactly(conversation);
    }

    @Test
    @DisplayName("Đổi tên, tải cả hội thoại về kèm nguồn và dấu nhận biết, xóa vào thùng rác")
    void renameExportAndDelete() throws Exception {
        User learner = learner();
        String lecture = paste(learner, "Bài giảng cây nhị phân", TREE);
        Stream first = ask(learner, Map.of("question", "Cách duyệt cây theo thứ tự giữa là gì?",
                "documentIds", List.of(lecture)));
        first.done();
        String conversation = first.meta().get("conversationId").asString();

        mvc.perform(patch(CONVERSATIONS + "/" + conversation).with(csrf()).cookie(learner.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"  Ôn   duyệt cây  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Ôn duyệt cây"));
        mvc.perform(patch(CONVERSATIONS + "/" + conversation).with(csrf()).cookie(learner.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(CONVERSATIONS + "/" + conversation).with(csrf()).cookie(learner.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + "a".repeat(121) + "\"}"))
                .andExpect(status().isBadRequest());

        MvcResult exported = mvc.perform(get(CONVERSATIONS + "/" + conversation + "/export").cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-AI-Generated", "true"))
                .andReturn();
        String file = exported.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(file).contains("ai_generated: true").contains("# Ôn duyệt cây")
                .contains("## Câu hỏi\n\nCách duyệt cây theo thứ tự giữa là gì?").contains("## Trả lời")
                .contains("1. Bài giảng cây nhị phân · Đoạn");
        assertThat(exported.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).contains(".md");

        mvc.perform(delete(CONVERSATIONS + "/" + conversation).with(csrf()).cookie(learner.cookies()))
                .andExpect(status().isNoContent());
        assertThat(ids(list(learner, null))).isEmpty();
        mvc.perform(get(CONVERSATIONS + "/" + conversation).cookie(learner.cookies()))
                .andExpect(status().isNotFound());
        assertThat(count("select count(*) from assistant_conversations where id = ?::uuid and trashed_at is not null",
                conversation)).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-05: người lạ mở, đổi tên, ghim, chuyển, tải về hay xóa hội thoại bằng mã: 404 và ghi nhật ký")
    void strangerGetsNotFound() throws Exception {
        User owner = learner();
        User stranger = learner();
        String conversation = start(owner, "Hội thoại riêng tư");
        String strangerSpace = workspace(stranger, "Không gian của người lạ");

        mvc.perform(get(CONVERSATIONS + "/" + conversation).cookie(stranger.cookies()))
                .andExpect(status().isNotFound());
        mvc.perform(patch(CONVERSATIONS + "/" + conversation).with(csrf()).cookie(stranger.cookies())
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Đổi\",\"pinned\":true}"))
                .andExpect(status().isNotFound());
        mvc.perform(put(CONVERSATIONS + "/" + conversation + "/workspace").with(csrf()).cookie(stranger.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"workspaceId\":\"" + strangerSpace + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get(CONVERSATIONS + "/" + conversation + "/export").cookie(stranger.cookies()))
                .andExpect(status().isNotFound());
        mvc.perform(delete(CONVERSATIONS + "/" + conversation).with(csrf()).cookie(stranger.cookies()))
                .andExpect(status().isNotFound());

        assertThat(count("select count(*) from audit_log where action = 'LEARNING_ACCESS_DENIED' and actor_id = ?::uuid "
                + "and object_type = 'CONVERSATION' and object_id = ?::uuid", stranger.id(), conversation)).isEqualTo(5);
        assertThat(ids(list(stranger, "riêng tư"))).isEmpty();
        mvc.perform(get(CONVERSATIONS + "/" + conversation).cookie(owner.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Hội thoại riêng tư"))
                .andExpect(jsonPath("$.pinned").value(false));
    }

    // ---------------------------------------------------------------- hỗ trợ

    /** Hội thoại riêng chưa chọn tài liệu: trợ lý nhắc chọn nguồn, không gọi mô hình. */
    private String start(User user, String question) throws Exception {
        Stream stream = ask(user, Map.of("question", question));
        assertThat(stream.done().get("status").asString()).isEqualTo("NO_SOURCES");
        return stream.meta().get("conversationId").asString();
    }

    private JsonNode list(User user, String query) throws Exception {
        return list(user, null, query);
    }

    private JsonNode list(User user, String workspaceId, String query) throws Exception {
        var request = get(CONVERSATIONS).cookie(user.cookies());
        if (workspaceId != null) {
            request.param("workspaceId", workspaceId);
        }
        if (query != null) {
            request.param("q", query);
        }
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
    }

    private static List<String> ids(JsonNode list) {
        List<String> ids = new ArrayList<>();
        list.forEach(item -> ids.add(item.get("id").asString()));
        return ids;
    }

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
        if (documents.length > 0) {
            mvc.perform(json(post("/api/v1/workspaces/" + id + "/documents"), user,
                    Map.of("documentIds", List.of(documents)))).andExpect(status().isOk());
        }
        return id;
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
        String email = "lichsu." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
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
