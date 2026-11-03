package com.learningcompanion.ai.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.learningcompanion.ai.quota.application.LearnerDay;
import com.learningcompanion.profile.domain.UserProfile;
import com.learningcompanion.profile.domain.UserProfileRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Ngày học và mốc hồi lượt theo múi giờ của người học (NCL-04-CN-003 TC-04, QTN-09). */
class LearnerDayTest {

    private final UserProfileRepository profiles = mock(UserProfileRepository.class);
    private final UUID owner = UUID.randomUUID();

    private LearnerDay at(String instant) {
        return new LearnerDay(profiles, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private void zone(String zone) {
        UserProfile profile = mock(UserProfile.class);
        when(profile.getTimeZone()).thenReturn(zone);
        when(profiles.findByOwnerId(owner)).thenReturn(Optional.of(profile));
    }

    @Test
    @DisplayName("17:30 UTC là 00:30 giờ Việt Nam hôm sau: đã sang ngày học mới, lượt hồi lúc nửa đêm kế tiếp")
    void vietnamMidnight() {
        zone("Asia/Ho_Chi_Minh");
        LearnerDay.Today today = at("2026-11-03T17:30:00Z").today(owner);
        assertThat(today.day()).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(today.resetsAt()).isEqualTo(Instant.parse("2026-11-04T17:00:00Z"));

        LearnerDay.Today before = at("2026-11-03T16:59:59Z").today(owner);
        assertThat(before.day()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(before.resetsAt()).isEqualTo(Instant.parse("2026-11-03T17:00:00Z"));
    }

    @Test
    @DisplayName("Chưa có hồ sơ, múi giờ trống hay sai thì theo giờ Việt Nam")
    void fallsBackToVietnam() {
        when(profiles.findByOwnerId(owner)).thenReturn(Optional.empty());
        assertThat(at("2026-11-03T10:00:00Z").today(owner).zone()).isEqualTo(LearnerDay.DEFAULT_ZONE);
        zone("  ");
        assertThat(at("2026-11-03T10:00:00Z").today(owner).zone()).isEqualTo(LearnerDay.DEFAULT_ZONE);
        zone("Sao/Hoa");
        assertThat(at("2026-11-03T10:00:00Z").today(owner).zone()).isEqualTo(LearnerDay.DEFAULT_ZONE);
    }

    @Test
    @DisplayName("Ngày đổi giờ mùa đông ở New York dài 25 giờ, lượt vẫn hồi đúng nửa đêm giờ địa phương")
    void daylightSavingDay() {
        zone("America/New_York");
        LearnerDay.Today today = at("2026-11-01T12:00:00Z").today(owner);
        assertThat(today.day()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(today.resetsAt()).isEqualTo(Instant.parse("2026-11-02T05:00:00Z"));
    }
}
