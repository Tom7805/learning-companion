package com.learningcompanion.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.learningcompanion.devtools.SampleDocuments;
import com.learningcompanion.library.application.DocumentService;
import com.learningcompanion.library.domain.ChunkLocation;
import com.learningcompanion.library.domain.Document;
import com.learningcompanion.library.domain.DocumentChunk;
import com.learningcompanion.library.domain.DocumentChunkRepository;
import com.learningcompanion.library.domain.DocumentRepository;
import com.learningcompanion.library.domain.DocumentStatus;
import com.learningcompanion.library.domain.DocumentType;
import com.learningcompanion.shared.mail.MockMailGateway;
import com.learningcompanion.support.PostgresTestConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

/** LC-33: API khung xem với PostgreSQL thật, vị trí đoạn và kiểm tra chủ sở hữu. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class ViewerIT {
    @Autowired MockMvc mvc;
    @Autowired MockMailGateway mailbox;
    @Autowired DocumentService service;
    @Autowired DocumentRepository documents;
    @Autowired DocumentChunkRepository chunks;
    @Autowired JdbcTemplate jdbc;

    @Test
    void pageTwelveThirdPassageAndRecent() throws Exception {
        User learner = learner();
        Document document = service.create(learner.id(), "Giáo trình 200 trang", DocumentType.PDF, "",
                "giao-trinh.pdf", "application/pdf", SampleDocuments.lecture(200, 0));
        document.markProcessed("Trang 12", 200, 3, "mock-primary", Instant.now());
        documents.save(document);
        for (int i = 1; i <= 3; i++) {
            chunks.save(DocumentChunk.create(document, i, ChunkLocation.of(ChunkLocation.Kind.PAGE, 12), null,
                    i == 3 ? "Đạo hàm riêng ở đoạn thứ ba" : "Đoạn " + i, DocumentChunk.Source.TEXT,
                    new float[] {0.1f}, Instant.now()));
        }
        String path = "/api/v1/library/documents/" + document.getId() + "/viewer";
        mvc.perform(get(path).cookie(learner.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processing.units").value(200))
                .andExpect(jsonPath("$.chunks[2].location.kind").value("PAGE"))
                .andExpect(jsonPath("$.chunks[2].location.number").value(12))
                .andExpect(jsonPath("$.chunks[2].content").value("Đạo hàm riêng ở đoạn thứ ba"));
        assertThat(jdbc.queryForObject("select last_opened_at from documents where id = ?", Instant.class,
                document.getId())).isNotNull();
    }

    @Test
    void processingHidesContentAndReportsPercent() throws Exception {
        User learner = learner();
        Document document = service.create(learner.id(), "Đang xử lý", DocumentType.TEXT, "bí mật",
                "a.txt", "text/plain", "bí mật".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        document.changeStatus(DocumentStatus.PROCESSING);
        documents.save(document);
        mvc.perform(get("/api/v1/library/documents/" + document.getId() + "/viewer")
                        .cookie(learner.cookies()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.percent").isNumber()).andExpect(jsonPath("$.chunks").isEmpty());
    }

    @Test
    void foreignViewerIs404AndAudited() throws Exception {
        User owner = learner();
        User stranger = learner();
        Document document = service.create(owner.id(), "Riêng tư", DocumentType.TEXT, "bí mật", "a.txt",
                "text/plain", "bí mật".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(get("/api/v1/library/documents/" + document.getId() + "/viewer")
                        .cookie(stranger.cookies()))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'LEARNING_ACCESS_DENIED' "
                + "and object_type = 'DOCUMENT' and actor_id = ? and object_id = ?", Integer.class,
                stranger.id(), document.getId())).isEqualTo(1);
    }

    private User learner() throws Exception {
        String email = "viewer." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/register")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"displayName":"Người học","email":"%s","password":"Xem-tai-lieu-2026!",
                         "acceptTerms":true,"termsVersion":"2026-10-01","privacyVersion":"2026-10-01"}"""
                        .formatted(email))).andExpect(status().isAccepted());
        Matcher matcher = Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)").matcher(
                String.join(" ", await().atMost(Duration.ofSeconds(60)).until(() ->
                        Optional.of(mailbox.messages().stream().filter(m -> m.to().equalsIgnoreCase(email)
                                && m.template().equals("VERIFY_EMAIL")).toList()).filter(l -> !l.isEmpty()),
                        Optional::isPresent).orElseThrow().get(0).links()));
        assertThat(matcher.find()).isTrue();
        MvcResult verified = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/auth/verify-email")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + matcher.group(1) + "\"}"))
                .andExpect(status().isOk()).andReturn();
        List<Cookie> jar = new ArrayList<>();
        for (String header : verified.getResponse().getHeaders(HttpHeaders.SET_COOKIE)) {
            String name = header.substring(0, header.indexOf('='));
            String value = header.substring(name.length() + 1, header.indexOf(';'));
            if (!value.isEmpty() && !header.contains("Max-Age=0")) jar.add(new Cookie(name, value));
        }
        String session = mvc.perform(get("/api/v1/auth/session").cookie(jar.toArray(Cookie[]::new)))
                .andReturn().getResponse().getContentAsString();
        return new User(UUID.fromString(JsonPath.read(session, "$.account.id")), jar);
    }

    private record User(UUID id, List<Cookie> jar) {
        Cookie[] cookies() { return jar.toArray(Cookie[]::new); }
    }
}
