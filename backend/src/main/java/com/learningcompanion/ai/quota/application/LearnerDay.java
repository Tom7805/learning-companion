package com.learningcompanion.ai.quota.application;

import com.learningcompanion.profile.domain.UserProfile;
import com.learningcompanion.profile.domain.UserProfileRepository;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Ngày học của người học theo múi giờ trong hồ sơ (QTN-09). Lượt hồi lại vào nửa đêm của múi giờ đó; hồ sơ chưa chọn
 * múi giờ thì theo giờ Việt Nam.
 */
@Component
public class LearnerDay {

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final UserProfileRepository profiles;
    private final Clock clock;

    public LearnerDay(UserProfileRepository profiles, Clock clock) {
        this.profiles = profiles;
        this.clock = clock;
    }

    public Today today(UUID ownerId) {
        ZoneId zone = zoneOf(ownerId);
        Instant now = clock.instant();
        LocalDate day = LocalDate.ofInstant(now, zone);
        return new Today(day, zone, day.plusDays(1).atStartOfDay(zone).toInstant(), now);
    }

    ZoneId zoneOf(UUID ownerId) {
        String zone = profiles.findByOwnerId(ownerId).map(UserProfile::getTimeZone).orElse(null);
        if (zone == null || zone.isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(zone);
        } catch (DateTimeException ex) {
            return DEFAULT_ZONE;
        }
    }

    /**
     * @param day      ngày học hiện tại của người học
     * @param resetsAt nửa đêm kế tiếp theo múi giờ của người học, lúc lượt hồi đủ
     */
    public record Today(LocalDate day, ZoneId zone, Instant resetsAt, Instant now) {
    }
}
