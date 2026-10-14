package com.learningcompanion.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.learningcompanion.notification.application.NotificationRetentionJob;
import com.learningcompanion.notification.application.NotificationService;
import com.learningcompanion.notification.domain.NotificationTargetType;
import com.learningcompanion.notification.domain.NotificationType;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** NCL-02-CN-002 — trung tâm thông báo trong ứng dụng, kiểm qua API như giao diện dùng. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class NotificationIT {

    private static final String PASSWORD = "song-xanh-mua-thu-2026";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MockMailGateway mailbox;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AdjustableClock clock;

    @Autowired
    private NotificationService notifications;

    @Autowired
    private NotificationRetentionJob retention;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    @DisplayName("TC-01: hai thông báo chưa đọc, mở thông báo mới nhất thì tới đúng gói dữ liệu và số chưa đọc còn một")
    void openingANotificationLeadsToItsObject() throws Exception {
        Learner lan = learner();
        login(lan.email());
        String exportId = exportReady(lan);

        JsonNode page = page(lan, null);
        assertThat(page.get("unreadCount").asLong()).isEqualTo(2);
        assertThat(types(page)).containsExactly("DATA_EXPORT_READY", "NEW_DEVICE_LOGIN");
        JsonNode newest = page.get("items").get(0);
        assertThat(newest.get("targetType").asString()).isEqualTo("DATA_EXPORT");
        assertThat(newest.has("readAt")).isFalse();
        assertThat(newest.get("params").get("fileCount").asInt()).isPositive();
        JsonNode device = page.get("items").get(1).get("params");
        assertThat(device.has("browser")).isTrue();
        assertThat(device.has("localNetwork")).isTrue();

        open(lan, newest.get("id").asString()).andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.path").value("/exports/" + exportId))
                .andExpect(jsonPath("$.unreadCount").value(1));
        unread(lan).andExpect(jsonPath("$.unreadCount").value(1));
        assertThat(page(lan, null).get("items").get(0).has("readAt")).isTrue();

        // Mở lại không đếm lại; cảnh báo thiết bị mới mở trang thiết bị.
        open(lan, newest.get("id").asString()).andExpect(jsonPath("$.unreadCount").value(1));
        open(lan, page.get("items").get(1).get("id").asString())
                .andExpect(jsonPath("$.path").value("/devices"))
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    @DisplayName("TC-02: cùng một sự kiện gửi hai lần do thử lại thì chỉ có một thông báo")
    void duplicateSendsAreIgnored() throws Exception {
        Learner lan = learner();
        UUID owner = UUID.fromString(lan.id());

        boolean first = notifications.notify(owner, NotificationType.DATA_EXPORT_READY,
                NotificationTargetType.DATA_EXPORT, UUID.randomUUID(), Map.of("fileCount", 3), "su-kien:42");
        boolean retry = notifications.notify(owner, NotificationType.DATA_EXPORT_READY,
                NotificationTargetType.DATA_EXPORT, UUID.randomUUID(), Map.of("fileCount", 3), "su-kien:42");

        assertThat(first).isTrue();
        assertThat(retry).isFalse();
        JsonNode page = page(lan, null);
        assertThat(page.get("items").size()).isEqualTo(1);
        assertThat(page.get("unreadCount").asLong()).isEqualTo(1);
        // Khóa chống trùng tính riêng cho từng người học.
        Learner minh = learner();
        assertThat(notifications.notify(UUID.fromString(minh.id()), NotificationType.DATA_EXPORT_READY,
                NotificationTargetType.DATA_EXPORT, UUID.randomUUID(), Map.of(), "su-kien:42")).isTrue();
    }

    @Test
    @DisplayName("TC-03: gói dữ liệu đã bị thay bằng gói mới thì thông báo cũ mở trang nội dung không còn, không báo lỗi")
    void deletedTargetOpensContentGone() throws Exception {
        Learner lan = learner();
        String firstExport = exportReady(lan);
        String first = page(lan, null).get("items").get(0).get("id").asString();

        clock.advance(Duration.ofDays(1));
        Learner tomorrow = login(lan.email());
        exportReady(tomorrow);
        assertThat(jdbc.queryForObject("select status from data_exports where id = ?::uuid", String.class,
                firstExport)).isEqualTo("EXPIRED");

        open(tomorrow, first).andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.path").doesNotExist());
        // Vẫn được tính là đã đọc.
        assertThat(jdbc.queryForObject("select read_at is not null from notifications where id = ?::uuid",
                Boolean.class, first)).isTrue();
    }

    @Test
    @DisplayName("TC-04: người học khác biết mã thông báo thì nhận không tìm thấy và lần từ chối được ghi nhật ký")
    void othersCannotOpenSomeoneElsesNotification() throws Exception {
        Learner lan = learner();
        notifications.notify(UUID.fromString(lan.id()), NotificationType.PASSWORD_CHANGED,
                NotificationTargetType.SECURITY, null, Map.of("change", "CHANGED"), "doi-mat-khau:1");
        String id = page(lan, null).get("items").get(0).get("id").asString();
        Learner minh = learner();

        open(minh, id).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/notifications").param("before", id).cookie(minh.cookies()))
                .andExpect(status().isNotFound());
        open(minh, UUID.randomUUID().toString()).andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("""
                select count(*) from audit_log
                where action = 'LEARNING_ACCESS_DENIED' and object_type = 'NOTIFICATION'
                  and object_id = ?::uuid and actor_id = ?::uuid""", Long.class, id, minh.id())).isEqualTo(2);
        // Thông báo của Lan không bị đánh dấu đã đọc, Minh không thấy gì của Lan.
        unread(lan).andExpect(jsonPath("$.unreadCount").value(1));
        assertThat(page(minh, null).get("items").size()).isZero();
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/notifications/" + id + "/open").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Đổi mật khẩu có cảnh báo trong trung tâm thông báo, mở ra trang bảo mật")
    void passwordChangeCreatesSecurityNotification() throws Exception {
        Learner lan = learner();
        mvc.perform(post("/api/v1/auth/password/change").with(csrf()).cookie(lan.cookies())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(PASSWORD,
                                "may-trang-bay-qua-doi-2026")))
                .andExpect(status().isOk());

        JsonNode item = page(lan, null).get("items").get(0);
        assertThat(item.get("type").asString()).isEqualTo("PASSWORD_CHANGED");
        assertThat(item.get("params").get("change").asString()).isEqualTo("CHANGED");
        open(lan, item.get("id").asString()).andExpect(jsonPath("$.path").value("/security"));
    }

    @Test
    @DisplayName("Đánh dấu tất cả đã đọc; bấm lại không đổi gì")
    void markAllRead() throws Exception {
        Learner lan = learner();
        UUID owner = UUID.fromString(lan.id());
        for (int i = 0; i < 3; i++) {
            notifications.notify(owner, NotificationType.PASSWORD_CHANGED, NotificationTargetType.SECURITY, null,
                    Map.of("change", "CHANGED"), "doi-" + i);
        }

        readAll(lan).andExpect(jsonPath("$.marked").value(3)).andExpect(jsonPath("$.unreadCount").value(0));
        readAll(lan).andExpect(jsonPath("$.marked").value(0));
        unread(lan).andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    @DisplayName("Danh sách mới nhất trước, phân trang không trùng không sót")
    void pagesNewestFirstWithoutGaps() throws Exception {
        Learner lan = learner();
        UUID owner = UUID.fromString(lan.id());
        for (int i = 0; i < 25; i++) {
            clock.advance(Duration.ofSeconds(1));
            notifications.notify(owner, NotificationType.PASSWORD_CHANGED, NotificationTargetType.SECURITY, null,
                    Map.of("n", i), "thu-tu-" + i);
        }

        JsonNode first = page(lan, null);
        assertThat(first.get("items").size()).isEqualTo(20);
        assertThat(first.get("items").get(0).get("params").get("n").asInt()).isEqualTo(24);
        String cursor = first.get("nextCursor").asString();
        JsonNode second = page(lan, cursor);
        assertThat(second.get("items").size()).isEqualTo(5);
        assertThat(second.has("nextCursor")).isFalse();
        List<Integer> order = new ArrayList<>();
        first.get("items").forEach(item -> order.add(item.get("params").get("n").asInt()));
        second.get("items").forEach(item -> order.add(item.get("params").get("n").asInt()));
        assertThat(order).hasSize(25).doesNotHaveDuplicates().isSortedAccordingTo((a, b) -> b - a);
    }

    @Test
    @DisplayName("Thông báo được giữ chín mươi ngày rồi tự xóa")
    void notificationsAreKeptNinetyDays() throws Exception {
        Learner lan = learner();
        UUID owner = UUID.fromString(lan.id());
        notifications.notify(owner, NotificationType.PASSWORD_CHANGED, NotificationTargetType.SECURITY, null,
                Map.of(), "cu");
        clock.advance(Duration.ofDays(2));
        notifications.notify(owner, NotificationType.PASSWORD_CHANGED, NotificationTargetType.SECURITY, null,
                Map.of(), "moi");

        clock.advance(Duration.ofDays(89));
        assertThat(retention.run()).isPositive();

        assertThat(jdbc.queryForList("select dedup_key from notifications where owner_id = ?::uuid", String.class,
                lan.id())).containsExactly("moi");
    }

    @Test
    @DisplayName("Số chưa đọc được đẩy ngay tới giao diện đang mở, không cần tải lại trang")
    void unreadCountIsPushedLive() throws Exception {
        Learner lan = learner();
        MvcResult stream = mvc.perform(get("/api/v1/notifications/stream").cookie(lan.cookies())
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
        await().atMost(Duration.ofSeconds(5)).until(() -> stream.getResponse().getContentAsString()
                .contains("\"unreadCount\":0"));

        notifications.notify(UUID.fromString(lan.id()), NotificationType.PASSWORD_CHANGED,
                NotificationTargetType.SECURITY, null, Map.of(), "truc-tiep");
        await().atMost(Duration.ofSeconds(5)).until(() -> stream.getResponse().getContentAsString()
                .contains("\"unreadCount\":1,\"created\":true"));

        readAll(lan).andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(5)).until(() -> stream.getResponse().getContentAsString()
                .contains("\"unreadCount\":0,\"created\":false"));
        assertThat(stream.getResponse().getContentAsString()).contains("event:unread");
    }

    // ---------------------------------------------------------------- hỗ trợ

    private String exportReady(Learner learner) throws Exception {
        String id = JSON.readTree(mvc.perform(post("/api/v1/exports").with(csrf()).cookie(learner.cookies()))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString())
                .get("latest").get("id").asString();
        await().atMost(Duration.ofSeconds(60)).until(() -> JSON.readTree(
                        mvc.perform(get("/api/v1/exports/" + id).cookie(learner.cookies()))
                                .andReturn().getResponse().getContentAsString()),
                node -> "READY".equals(node.path("status").asString()));
        return id;
    }

    private JsonNode page(Learner learner, String before) throws Exception {
        var request = get("/api/v1/notifications").cookie(learner.cookies());
        if (before != null) {
            request.param("before", before);
        }
        return JSON.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
    }

    private static List<String> types(JsonNode page) {
        List<String> types = new ArrayList<>();
        page.get("items").forEach(item -> types.add(item.get("type").asString()));
        return types;
    }

    private ResultActions open(Learner learner, String id) throws Exception {
        return mvc.perform(post("/api/v1/notifications/" + id + "/open").with(csrf()).cookie(learner.cookies()));
    }

    private ResultActions unread(Learner learner) throws Exception {
        return mvc.perform(get("/api/v1/notifications/unread-count").cookie(learner.cookies()))
                .andExpect(status().isOk());
    }

    private ResultActions readAll(Learner learner) throws Exception {
        return mvc.perform(post("/api/v1/notifications/read-all").with(csrf()).cookie(learner.cookies()))
                .andExpect(status().isOk());
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
        String email = "thong.bao." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
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

    /** Đăng nhập từ một trình duyệt mới (chưa có cookie thiết bị): tài khoản nhận cảnh báo thiết bị mới. */
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
