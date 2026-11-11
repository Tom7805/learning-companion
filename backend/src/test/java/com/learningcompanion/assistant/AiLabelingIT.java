package com.learningcompanion.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

/**
 * NCL-04-CN-005 — gắn nhãn nội dung do trí tuệ nhân tạo sinh ra: thông báo lần đầu mở trợ lý, nhãn trên câu trả lời cũ,
 * tệp tải về có dấu nhận biết trong siêu dữ liệu, và chặn tải khi bộ đính dấu lỗi.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class AiLabelingIT {

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
    @DisplayName("TC-01: tài khoản mới chưa đọc thông báo trí tuệ nhân tạo; đã bấm đã hiểu thì không hiện lại")
    void disclosureShownOnceForNewAccount() throws Exception {
        User learner = learner();
        mvc.perform(get("/api/v1/assistant/disclosure").cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acknowledged").value(false))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(post("/api/v1/assistant/disclosure").with(csrf()).cookie(learner.cookies()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.acknowledged").value(true));
        mvc.perform(post("/api/v1/assistant/disclosure").with(csrf()).cookie(learner.cookies()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/assistant/disclosure").cookie(learner.cookies()))
                .andExpect(jsonPath("$.acknowledged").value(true))
                .andExpect(jsonPath("$.acknowledgedAt").isNotEmpty());
        assertThat(count("select count(*) from ai_disclosure_acknowledgements where owner_id = ?::uuid",
                learner.id())).isEqualTo(1);
        // Người học khác vẫn chưa đọc.
        mvc.perform(get("/api/v1/assistant/disclosure").cookie(learner().cookies()))
                .andExpect(jsonPath("$.acknowledged").value(false));
    }

    @Test
    @DisplayName("TC-02/03: câu trả lời cũ vẫn gắn nhãn; tải về có dấu ai_generated trong siêu dữ liệu và danh sách nguồn")
    void answerLabelledAndExportedWithWatermark() throws Exception {
        User learner = learner();
        String lecture = paste(learner, "Bài giảng cây nhị phân", TREE);
        Stream answer = ask(learner, Map.of("question", "Cách duyệt cây theo thứ tự giữa là gì?",
                "documentIds", List.of(lecture)));
        String conversation = answer.meta().get("conversationId").asString();
        String answerId = answer.meta().get("answerId").asString();
        String questionId = answer.meta().get("questionId").asString();

        mvc.perform(get("/api/v1/assistant/conversations/" + conversation).cookie(learner.cookies()))
                .andExpect(jsonPath("$.messages[0].aiGenerated").value(false))
                .andExpect(jsonPath("$.messages[1].aiGenerated").value(true));

        MvcResult exported = mvc.perform(get("/api/v1/assistant/conversations/" + conversation + "/messages/"
                        + answerId + "/export").cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-AI-Generated", "true"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("attachment")))
                .andReturn();
        assertThat(exported.getResponse().getContentType()).startsWith("text/markdown");
        String file = exported.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(file).startsWith("---\n");
        String meta = file.substring(4, file.indexOf("\n---\n"));
        assertThat(meta).contains("ai_generated: true", "ai_feature: ASSISTANT_CHAT", "content_id: " + answerId,
                "edited: false", "generator: \"Learning Companion\"").containsPattern("content_sha256: [0-9a-f]{64}");
        assertThat(file).contains("> Nội dung do trí tuệ nhân tạo sinh ra", "## Nguồn", "1. Bài giảng cây nhị phân");

        // Câu hỏi của người học không phải nội dung do trí tuệ nhân tạo sinh ra: không tải được như câu trả lời.
        mvc.perform(get("/api/v1/assistant/conversations/" + conversation + "/messages/" + questionId + "/export")
                .cookie(learner.cookies())).andExpect(status().isNotFound());
        // Người khác tải bằng mã: 404, không lộ nội dung.
        mvc.perform(get("/api/v1/assistant/conversations/" + conversation + "/messages/" + answerId + "/export")
                        .cookie(learner().cookies()))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("X-AI-Generated"));
    }

    @Test
    @DisplayName("TC-04: bộ đính dấu nhận biết lỗi thì chặn tải và báo lỗi tạm thời, không xuất tệp thiếu dấu")
    void exportBlockedWhenWatermarkFails() throws Exception {
        User learner = learner();
        String lecture = paste(learner, "Bài giảng cây nhị phân", TREE);
        Stream answer = ask(learner, Map.of("question", "Cách duyệt cây theo thứ tự giữa là gì?",
                "documentIds", List.of(lecture)));
        String url = "/api/v1/assistant/conversations/" + answer.meta().get("conversationId").asString()
                + "/messages/" + answer.meta().get("answerId").asString() + "/export";

        mvc.perform(post("/api/v1/dev/ai/label-failures").contentType(MediaType.APPLICATION_JSON)
                .content("{\"calls\":1}")).andExpect(status().isNoContent());
        MvcResult blocked = mvc.perform(get(url).cookie(learner.cookies()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_LABEL_UNAVAILABLE"))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION))
                .andReturn();
        assertThat(blocked.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .doesNotContain("thứ tự giữa");
        // Lỗi tạm thời: lần sau bộ đính dấu chạy lại được thì tải bình thường.
        mvc.perform(get(url).cookie(learner.cookies())).andExpect(status().isOk());
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
