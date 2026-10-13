package com.learningcompanion.appshell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.ResultActions;

/** NCL-02-CN-001 — tùy chọn giao diện theo tài khoản (chế độ sáng tối, cỡ chữ, thanh bên thu gọn), kiểm qua API. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class UiPreferencesIT {

    private static final String PASSWORD = "song-xanh-mua-thu-2026";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MockMailGateway mailbox;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Chưa chọn gì thì trả mặc định (theo hệ thống, cỡ vừa, thanh bên mở) mà không ghi bản ghi nào")
    void defaultsWithoutStoring() throws Exception {
        Learner lan = learner();

        preferences(lan).andExpect(status().isOk())
                .andExpect(jsonPath("$.themeMode").value("SYSTEM"))
                .andExpect(jsonPath("$.fontSize").value("MEDIUM"))
                .andExpect(jsonPath("$.sidebarCollapsed").value(false));
        assertThat(rows(lan)).isZero();
    }

    @Test
    @DisplayName("TC-01: thu gọn thanh bên được nhớ; đổi từng tùy chọn không làm mất tùy chọn khác, đăng nhập máy khác vẫn thấy")
    void changesArePartialAndFollowTheAccount() throws Exception {
        Learner lan = learner();

        change(lan, "{\"sidebarCollapsed\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.sidebarCollapsed").value(true))
                .andExpect(jsonPath("$.themeMode").value("SYSTEM"));
        change(lan, "{\"themeMode\":\"dark\",\"fontSize\":\"LARGE\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.themeMode").value("DARK"))
                .andExpect(jsonPath("$.fontSize").value("LARGE"))
                .andExpect(jsonPath("$.sidebarCollapsed").value(true));

        Learner otherDevice = login(lan.email());
        preferences(otherDevice).andExpect(jsonPath("$.themeMode").value("DARK"))
                .andExpect(jsonPath("$.fontSize").value("LARGE"))
                .andExpect(jsonPath("$.sidebarCollapsed").value(true));
        assertThat(rows(lan)).isEqualTo(1);
    }

    @Test
    @DisplayName("Giá trị lạ bị từ chối và không đổi gì")
    void invalidValuesAreRejected() throws Exception {
        Learner lan = learner();

        change(lan, "{\"themeMode\":\"PURPLE\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PREFERENCE"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("themeMode"));
        change(lan, "{\"fontSize\":\"HUGE\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("fontSize"));
        assertThat(rows(lan)).isZero();
    }

    @Test
    @DisplayName("Mỗi người chỉ thấy tùy chọn của mình; chưa đăng nhập thì 401")
    void preferencesArePerAccount() throws Exception {
        Learner lan = learner();
        Learner minh = learner();
        change(lan, "{\"themeMode\":\"DARK\"}").andExpect(status().isOk());

        preferences(minh).andExpect(jsonPath("$.themeMode").value("SYSTEM"));
        mvc.perform(get("/api/v1/ui-preferences")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- hỗ trợ

    private ResultActions preferences(Learner learner) throws Exception {
        return mvc.perform(get("/api/v1/ui-preferences").cookie(learner.cookies()));
    }

    private ResultActions change(Learner learner, String body) throws Exception {
        return mvc.perform(patch("/api/v1/ui-preferences").with(csrf()).cookie(learner.cookies())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long rows(Learner learner) {
        return jdbc.queryForObject("select count(*) from ui_preferences where owner_id = ?::uuid", Long.class,
                learner.id());
    }

    private record Learner(String id, String email, List<Cookie> jar) {

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

    private Learner learner() throws Exception {
        String email = "giao.dien." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
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
        return withId(email, jar);
    }

    private Learner login(String email) throws Exception {
        List<Cookie> jar = new ArrayList<>();
        absorb(jar, mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"rememberDevice\":false}"
                                .formatted(email, PASSWORD)))
                .andExpect(status().isOk()).andReturn());
        return withId(email, jar);
    }

    private Learner withId(String email, List<Cookie> jar) throws Exception {
        String session = mvc.perform(get("/api/v1/auth/session").cookie(jar.toArray(Cookie[]::new)))
                .andReturn().getResponse().getContentAsString();
        String id = session.replaceAll(".*\"account\":\\{\"id\":\"([^\"]+)\".*", "$1");
        return new Learner(id, email, jar);
    }

    private MockMailGateway.CapturedMail awaitMail(String to) {
        return await().atMost(Duration.ofSeconds(60))
                .until(() -> Optional.of(mailbox.messages().stream()
                                .filter(mail -> mail.to().equalsIgnoreCase(to) && mail.template().equals("VERIFY_EMAIL"))
                                .toList()).filter(list -> !list.isEmpty()), Optional::isPresent)
                .orElseThrow().get(0);
    }
}
