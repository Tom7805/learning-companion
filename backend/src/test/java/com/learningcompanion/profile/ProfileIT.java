package com.learningcompanion.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.learningcompanion.profile.application.TimeZoneChangedEvent;
import com.learningcompanion.shared.mail.MockMailGateway;
import com.learningcompanion.support.PostgresTestConfiguration;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** NCL-01-CN-005 — hồ sơ và tùy chọn học tập cá nhân, kiểm qua API với PostgreSQL thật. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PostgresTestConfiguration.class, ProfileIT.Events.class})
class ProfileIT {

    private static final String PASSWORD = "song-xanh-mua-thu-2026";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MockMailGateway mailbox;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Events events;

    @BeforeEach
    void reset() {
        events.timeZoneChanges.clear();
    }

    @Test
    @DisplayName("Lần đầu mở hồ sơ nhận giá trị mặc định: mục tiêu 30 phút, đổi ngày lúc 4 giờ sáng, trợ lý trả lời tiếng Việt")
    void firstOpenCreatesDefaults() throws Exception {
        Browser lan = new Browser();
        verifiedAccount(lan);

        me(lan).andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Lan Anh"))
                .andExpect(jsonPath("$.timeZone").doesNotExist())
                .andExpect(jsonPath("$.dailyGoalMinutes").value(30))
                .andExpect(jsonPath("$.dayRolloverHour").value(4))
                .andExpect(jsonPath("$.assistantLanguage").value("vi"))
                .andExpect(jsonPath("$.avatarUrl").doesNotExist());
        // Mở lại không tạo thêm hồ sơ.
        String first = profileId(lan);
        assertThat(profileId(lan)).isEqualTo(first);
    }

    @Test
    @DisplayName("TC-01: chọn múi giờ Việt Nam và mục tiêu ba mươi phút thì hồ sơ được lưu")
    void savesVietnamTimeZoneAndGoal() throws Exception {
        Browser lan = new Browser();
        verifiedAccount(lan);
        String id = profileId(lan);

        update(lan, id, Map.of("timeZone", "Asia/Ho_Chi_Minh")).andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Asia/Ho_Chi_Minh"));
        update(lan, id, Map.of("dailyGoalMinutes", 45)).andExpect(jsonPath("$.dailyGoalMinutes").value(45));
        update(lan, id, Map.of("dailyGoalMinutes", 30, "assistantLanguage", "en", "dayRolloverHour", 5))
                .andExpect(status().isOk());

        me(lan).andExpect(jsonPath("$.timeZone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.dailyGoalMinutes").value(30))
                .andExpect(jsonPath("$.assistantLanguage").value("en"))
                .andExpect(jsonPath("$.dayRolloverHour").value(5));
        List<String> details = jdbc.queryForList("""
                select details::text from audit_log where action = 'PROFILE_UPDATED' and object_id = ?
                order by occurred_at""", String.class, UUID.fromString(id));
        assertThat(details).hasSize(3);
        assertThat(details.get(0)).contains("timeZone").doesNotContain("Ho_Chi_Minh");
    }

    @Test
    @DisplayName("TC-02: tên hiển thị sáu mươi ký tự bị từ chối, báo tối đa năm mươi ký tự và không lưu")
    void displayNameLongerThanFiftyIsRejected() throws Exception {
        Browser lan = new Browser();
        verifiedAccount(lan);
        String id = profileId(lan);

        update(lan, id, Map.of("displayName", "Ă".repeat(60)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DISPLAY_NAME_TOO_LONG"))
                .andExpect(jsonPath("$.message").value("Tên hiển thị tối đa 50 ký tự."))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("displayName"));
        update(lan, id, Map.of("displayName", "   ")).andExpect(jsonPath("$.code").value("DISPLAY_NAME_REQUIRED"));
        me(lan).andExpect(jsonPath("$.displayName").value("Lan Anh"));

        // Năm mươi ký tự có dấu vẫn hợp lệ, khoảng trắng thừa được gộp lại.
        String fifty = "Nguyễn  Thị Lan Anh " + "ơ".repeat(31);
        update(lan, id, Map.of("displayName", fifty)).andExpect(status().isOk());
        session(lan).andExpect(jsonPath("$.account.displayName").value("Nguyễn Thị Lan Anh " + "ơ".repeat(31)));
    }

    @Test
    @DisplayName("Giá trị tùy chọn ngoài phạm vi bị từ chối")
    void invalidPreferencesAreRejected() throws Exception {
        Browser lan = new Browser();
        verifiedAccount(lan);
        String id = profileId(lan);

        update(lan, id, Map.of("timeZone", "Sao_Hoa/Thu_Do")).andExpect(jsonPath("$.code").value("INVALID_TIME_ZONE"));
        update(lan, id, Map.of("timeZone", "+07:00")).andExpect(jsonPath("$.code").value("INVALID_TIME_ZONE"));
        update(lan, id, Map.of("dailyGoalMinutes", 0)).andExpect(jsonPath("$.fieldErrors[0].field")
                .value("dailyGoalMinutes"));
        update(lan, id, Map.of("dayRolloverHour", 24)).andExpect(status().isBadRequest());
        update(lan, id, Map.of("assistantLanguage", "fr")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-04: đổi sang múi giờ chậm hơn bảy giờ thì lưu thay đổi và phát sự kiện để tính lại các mốc")
    void timeZoneChangeIsSavedAndAnnounced() throws Exception {
        Browser lan = new Browser();
        verifiedAccount(lan);
        String id = profileId(lan);
        update(lan, id, Map.of("timeZone", "Asia/Ho_Chi_Minh"));

        update(lan, id, Map.of("timeZone", "Africa/Abidjan")).andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Africa/Abidjan"));

        assertThat(events.timeZoneChanges).extracting(TimeZoneChangedEvent::newTimeZone)
                .containsExactly("Asia/Ho_Chi_Minh", "Africa/Abidjan");
        assertThat(events.timeZoneChanges.get(1).previousTimeZone()).isEqualTo("Asia/Ho_Chi_Minh");
        // Gửi lại đúng múi giờ đang dùng thì không phát thêm sự kiện.
        update(lan, id, Map.of("timeZone", "Africa/Abidjan"));
        assertThat(events.timeZoneChanges).hasSize(2);
    }

    @Test
    @DisplayName("TC-03: tệp văn bản đổi đuôi làm ảnh đại diện bị từ chối và nêu các định dạng được nhận")
    void textFileAsAvatarIsRejected() throws Exception {
        Browser lan = new Browser();
        verifiedAccount(lan);
        String id = profileId(lan);
        MockMultipartFile fake = new MockMultipartFile("file", "anh-dai-dien.png", "image/png",
                "Ghi chú học tập, không phải ảnh".getBytes(StandardCharsets.UTF_8));

        avatar(lan, id, fake)
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("AVATAR_INVALID_FORMAT"))
                .andExpect(jsonPath("$.details.acceptedFormats[0]").value("JPG"))
                .andExpect(jsonPath("$.details.acceptedFormats[1]").value("PNG"))
                .andExpect(jsonPath("$.details.acceptedFormats[2]").value("WebP"));
        me(lan).andExpect(jsonPath("$.avatarUrl").doesNotExist());
    }

    @Test
    @DisplayName("Ảnh đại diện hợp lệ được cắt vuông, chỉ chủ sở hữu mở được, đổi ảnh thì xóa ảnh cũ khỏi kho")
    void validAvatarIsStoredSquare() throws Exception {
        Browser lan = new Browser();
        verifiedAccount(lan);
        String id = profileId(lan);
        MockMultipartFile photo = new MockMultipartFile("file", "chan-dung.jpg", "image/jpeg",
                AvatarProcessorTest.encode(new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB), "jpg"));

        String url = JSON.readTree(avatar(lan, id, photo).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("avatarUrl").asString();
        assertThat(url).startsWith("/api/v1/profiles/" + id + "/avatar?v=");
        String firstKey = storageKey(id);

        MvcResult image = mvc.perform(get(url).cookie(lan.cookies()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andReturn();
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(image.getResponse().getContentAsByteArray()));
        assertThat(stored.getWidth()).isEqualTo(256);
        assertThat(stored.getHeight()).isEqualTo(256);

        Browser minh = new Browser();
        verifiedAccount(minh);
        mvc.perform(get(url).cookie(minh.cookies())).andExpect(status().isNotFound());

        avatar(lan, id, photo).andExpect(status().isOk());
        assertThat(storageKey(id)).isNotEqualTo(firstKey);
        mvc.perform(delete("/api/v1/profiles/" + id + "/avatar").with(csrf()).cookie(lan.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").doesNotExist());
        assertThat(storageKey(id)).isNull();
    }

    @Test
    @DisplayName("TC-05: người khác biết mã hồ sơ gửi yêu cầu sửa thì nhận 404 và lần từ chối được ghi nhật ký")
    void anotherLearnerCannotEditProfile() throws Exception {
        Browser lan = new Browser();
        verifiedAccount(lan);
        String lansProfile = profileId(lan);
        Browser minh = new Browser();
        verifiedAccount(minh);

        String foreign = update(minh, lansProfile, Map.of("displayName", "Kẻ mạo danh", "dailyGoalMinutes", 5))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
        assertThat(foreign).doesNotContain("Lan Anh");
        avatar(minh, lansProfile, new MockMultipartFile("file", "x.png", "image/png", new byte[] {1}))
                .andExpect(status().isNotFound());
        update(minh, UUID.randomUUID().toString(), Map.of("dailyGoalMinutes", 5)).andExpect(status().isNotFound());

        me(lan).andExpect(jsonPath("$.displayName").value("Lan Anh")).andExpect(jsonPath("$.dailyGoalMinutes").value(30));
        List<Map<String, Object>> denials = jdbc.queryForList("""
                select object_type, details::text as details from audit_log
                where action = 'LEARNING_ACCESS_DENIED' and object_id = ?""", UUID.fromString(lansProfile));
        assertThat(denials).hasSize(2);
        assertThat(denials).allSatisfy(row -> {
            assertThat(row.get("object_type")).isEqualTo("PROFILE");
            assertThat((String) row.get("details")).doesNotContain("Kẻ mạo danh");
        });
    }

    // ----- hỗ trợ -----

    /** Ghi lại sự kiện đổi múi giờ để kiểm. */
    @TestConfiguration
    static class Events {
        final List<TimeZoneChangedEvent> timeZoneChanges = new CopyOnWriteArrayList<>();

        @EventListener
        void on(TimeZoneChangedEvent event) {
            timeZoneChanges.add(event);
        }
    }

    private static final class Browser {
        private final List<Cookie> jar = new ArrayList<>();

        Cookie[] cookies() {
            return jar.isEmpty() ? new Cookie[] {new Cookie("lc_none", "1")} : jar.toArray(Cookie[]::new);
        }

        void absorb(MvcResult result) {
            for (String header : result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)) {
                String name = header.substring(0, header.indexOf('='));
                String value = header.substring(name.length() + 1, header.indexOf(';'));
                jar.removeIf(cookie -> cookie.getName().equals(name));
                if (!value.isEmpty() && !header.contains("Max-Age=0")) {
                    jar.add(new Cookie(name, value));
                }
            }
        }
    }

    private void verifiedAccount(Browser browser) throws Exception {
        String email = "ho.so." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Lan Anh","email":"%s","password":"%s","acceptTerms":true,
                                 "termsVersion":"2026-10-01","privacyVersion":"2026-10-01"}"""
                                .formatted(email, PASSWORD)))
                .andExpect(status().isAccepted());
        MockMailGateway.CapturedMail mail = await().atMost(Duration.ofSeconds(60)).until(() -> mailbox.messages()
                .stream().filter(m -> m.to().equalsIgnoreCase(email) && m.template().equals("VERIFY_EMAIL"))
                .findFirst(), Optional::isPresent).orElseThrow();
        Matcher matcher = Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)")
                .matcher(String.join(" ", mail.links()));
        assertThat(matcher.find()).isTrue();
        browser.absorb(mvc.perform(post("/api/v1/auth/verify-email").with(csrf()).cookie(browser.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + matcher.group(1) + "\"}"))
                .andExpect(status().isOk()).andReturn());
    }

    private ResultActions me(Browser browser) throws Exception {
        return mvc.perform(get("/api/v1/profiles/me").cookie(browser.cookies()));
    }

    private ResultActions session(Browser browser) throws Exception {
        return mvc.perform(get("/api/v1/auth/session").cookie(browser.cookies()));
    }

    private String profileId(Browser browser) throws Exception {
        JsonNode profile = JSON.readTree(me(browser).andReturn().getResponse().getContentAsString());
        return profile.get("id").asString();
    }

    private ResultActions update(Browser browser, String id, Map<String, Object> changes) throws Exception {
        return mvc.perform(patch("/api/v1/profiles/" + id).with(csrf()).cookie(browser.cookies())
                .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(changes)));
    }

    private ResultActions avatar(Browser browser, String id, MockMultipartFile file) throws Exception {
        return mvc.perform(multipart(HttpMethod.PUT, "/api/v1/profiles/" + id + "/avatar").file(file).with(csrf())
                .cookie(browser.cookies()));
    }

    private String storageKey(String profileId) {
        return jdbc.queryForObject("select avatar_storage_key from user_profiles where id = ?", String.class,
                UUID.fromString(profileId));
    }
}
