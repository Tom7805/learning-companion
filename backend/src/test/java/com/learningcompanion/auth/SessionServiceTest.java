package com.learningcompanion.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.learningcompanion.auth.domain.DeviceInfo;
import com.learningcompanion.auth.domain.UserSession;
import com.learningcompanion.auth.infrastructure.UserAgentParser;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Quy tắc thời hạn phiên (QTN-02) và nhận diện thiết bị cho danh sách thiết bị. */
class SessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T03:00:00Z");
    private static final Duration IDLE = Duration.ofHours(2);
    private static final DeviceInfo DEVICE =
            new DeviceInfo("Chrome", "Windows", DeviceInfo.DeviceType.DESKTOP, null, true);

    @Test
    void rememberedSessionLastsThirtyDaysAndActivityDoesNotExtendIt() {
        UserSession session = start(true, Duration.ofDays(30));

        session.touch(NOW.plus(Duration.ofDays(10)), IDLE);

        assertThat(session.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(session.getLastActiveAt()).isEqualTo(NOW.plus(Duration.ofDays(10)));
        assertThat(session.state(NOW.plus(Duration.ofDays(29)))).isEqualTo(UserSession.State.ACTIVE);
        assertThat(session.state(NOW.plus(Duration.ofDays(30)))).isEqualTo(UserSession.State.EXPIRED);
    }

    @Test
    void idleSessionExpiresAfterTwoHoursWithoutActivity() {
        UserSession session = start(false, IDLE);

        assertThat(session.state(NOW.plus(Duration.ofMinutes(119)))).isEqualTo(UserSession.State.ACTIVE);
        assertThat(session.state(NOW.plus(IDLE))).isEqualTo(UserSession.State.EXPIRED);
    }

    @Test
    void activityKeepsIdleSessionAlive() {
        UserSession session = start(false, IDLE);

        session.touch(NOW.plus(Duration.ofMinutes(90)), IDLE);

        assertThat(session.state(NOW.plus(Duration.ofMinutes(200)))).isEqualTo(UserSession.State.ACTIVE);
        assertThat(session.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(210)));
    }

    @Test
    void revokedSessionStaysRevoked() {
        UserSession session = start(true, Duration.ofDays(30));

        session.revoke(NOW.plusSeconds(10), "REVOKED_BY_USER");
        session.revoke(NOW.plusSeconds(20), "LOGOUT");

        assertThat(session.state(NOW.plusSeconds(30))).isEqualTo(UserSession.State.REVOKED);
        assertThat(session.getRevokeReason()).isEqualTo("REVOKED_BY_USER");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36|Chrome|Windows|DESKTOP",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/140.0 Safari/537.36 Edg/140.0|Edge|Windows|DESKTOP",
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 Chrome/140.0 Mobile Safari/537.36|Chrome|Android|MOBILE",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Version/18.0 Mobile/15E148 Safari/604.1|Safari|iOS|MOBILE",
            "Mozilla/5.0 (iPad; CPU OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Version/18.0 Safari/604.1|Safari|iPadOS|TABLET",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6; rv:131.0) Gecko/20100101 Firefox/131.0|Firefox|macOS|DESKTOP",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/138.0 coc_coc_browser/138.0 Safari/537.36|Cốc Cốc|Windows|DESKTOP"
    })
    void recognisesCommonBrowsers(String userAgent, String browser, String os, String type) {
        UserAgentParser.Parsed parsed = new UserAgentParser().parse(userAgent);

        assertThat(parsed.browser()).isEqualTo(browser);
        assertThat(parsed.operatingSystem()).isEqualTo(os);
        assertThat(parsed.deviceType().name()).isEqualTo(type);
    }

    @Test
    void unknownUserAgentDoesNotBreak() {
        UserAgentParser.Parsed parsed = new UserAgentParser().parse(null);
        assertThat(parsed.browser()).isEqualTo("Không rõ");
        assertThat(parsed.deviceType()).isEqualTo(DeviceInfo.DeviceType.DESKTOP);
    }

    private static UserSession start(boolean remember, Duration lifetime) {
        return UserSession.start(UUID.randomUUID(), "hash", NOW, remember, lifetime, "device", DEVICE,
                "JUnit", "127.0.0.1");
    }
}
