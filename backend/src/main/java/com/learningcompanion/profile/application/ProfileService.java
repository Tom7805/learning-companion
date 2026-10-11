package com.learningcompanion.profile.application;

import com.learningcompanion.auth.application.RegistrationService;
import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.profile.domain.UserProfile;
import com.learningcompanion.profile.domain.UserProfileRepository;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.ownership.OwnerContext;
import com.learningcompanion.shared.ownership.OwnershipGuard;
import com.learningcompanion.shared.storage.FileStorageService;
import com.learningcompanion.shared.web.ClientInfo;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Hồ sơ và tùy chọn học tập cá nhân (NCL-01-CN-005); luôn trong phạm vi chủ sở hữu của phiên. */
@Service
public class ProfileService {

    public static final int DISPLAY_NAME_MAX = 50;
    public static final int GOAL_MIN = 5;
    public static final int GOAL_MAX = 600;
    private static final Set<String> LANGUAGES = Set.of("vi", "en");

    private final UserProfileRepository profiles;
    private final UserAccountRepository accounts;
    private final OwnerContext owner;
    private final OwnershipGuard guard;
    private final AvatarProcessor avatars;
    private final FileStorageService storage;
    private final AuditLogger audit;
    private final ApplicationEventPublisher events;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ProfileService(UserProfileRepository profiles, UserAccountRepository accounts, OwnerContext owner,
                          OwnershipGuard guard, AvatarProcessor avatars, FileStorageService storage,
                          AuditLogger audit, ApplicationEventPublisher events, JdbcTemplate jdbc, Clock clock) {
        this.profiles = profiles;
        this.accounts = accounts;
        this.owner = owner;
        this.guard = guard;
        this.avatars = avatars;
        this.storage = storage;
        this.audit = audit;
        this.events = events;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Hồ sơ của người đang đăng nhập; tạo với giá trị mặc định ở lần mở đầu tiên. */
    @Transactional
    public ProfileView me() {
        UUID ownerId = owner.requireOwnerId();
        UserProfile profile = profiles.findByOwnerId(ownerId).orElseGet(() -> createDefault(ownerId));
        return view(profile);
    }

    /** Sửa một hoặc nhiều tùy chọn; giao diện gửi từng trường ngay khi người học đổi. */
    @Transactional
    public ProfileView update(UUID profileId, ProfileChanges changes, ClientInfo client) {
        UserProfile profile = guard.require(profiles, profileId, AuditObjectType.PROFILE, client);
        UserAccount account = account(profile.getOwnerId());
        Instant now = clock.instant();
        List<String> changed = new ArrayList<>();

        if (changes.displayName() != null) {
            String name = validDisplayName(changes.displayName());
            if (!name.equals(account.getDisplayName())) {
                account.rename(name, now);
                changed.add("displayName");
            }
        }
        if (changes.timeZone() != null) {
            String zone = validTimeZone(changes.timeZone());
            String previous = profile.getTimeZone();
            if (!zone.equals(previous)) {
                profile.changeTimeZone(zone, now);
                changed.add("timeZone");
                events.publishEvent(new TimeZoneChangedEvent(profile.getOwnerId(), previous, zone));
            }
        }
        if (changes.assistantLanguage() != null) {
            if (!LANGUAGES.contains(changes.assistantLanguage())) {
                throw new BusinessException(ErrorCode.INVALID_PREFERENCE, "assistantLanguage");
            }
            if (!changes.assistantLanguage().equals(profile.getAssistantLanguage())) {
                profile.changeAssistantLanguage(changes.assistantLanguage(), now);
                changed.add("assistantLanguage");
            }
        }
        if (changes.dailyGoalMinutes() != null) {
            int minutes = changes.dailyGoalMinutes();
            if (minutes < GOAL_MIN || minutes > GOAL_MAX) {
                throw new BusinessException(ErrorCode.INVALID_PREFERENCE, "dailyGoalMinutes",
                        Map.of("min", GOAL_MIN, "max", GOAL_MAX));
            }
            if (minutes != profile.getDailyGoalMinutes()) {
                profile.changeDailyGoal(minutes, now);
                changed.add("dailyGoalMinutes");
            }
        }
        if (changes.dayRolloverHour() != null) {
            int hour = changes.dayRolloverHour();
            if (hour < 0 || hour > 23) {
                throw new BusinessException(ErrorCode.INVALID_PREFERENCE, "dayRolloverHour");
            }
            if (hour != profile.getDayRolloverHour()) {
                profile.changeDayRolloverHour(hour, now);
                changed.add("dayRolloverHour");
            }
        }
        if (!changed.isEmpty()) {
            // Chỉ ghi tên trường đã đổi, không ghi giá trị.
            audit.byUser(profile.getOwnerId(), AuditAction.PROFILE_UPDATED, AuditObjectType.PROFILE, profile.getId(),
                    Map.of("fields", changed), client);
        }
        return view(profile, account);
    }

    @Transactional
    public ProfileView replaceAvatar(UUID profileId, byte[] upload, ClientInfo client) {
        UserProfile profile = guard.require(profiles, profileId, AuditObjectType.PROFILE, client);
        byte[] normalized = avatars.process(upload);
        String key = storage.store(profile.getOwnerId(), normalized);
        String previous = profile.replaceAvatar(key, clock.instant());
        if (previous != null) {
            storage.delete(previous);
        }
        audit.byUser(profile.getOwnerId(), AuditAction.AVATAR_UPDATED, AuditObjectType.PROFILE, profile.getId(),
                Map.of(), client);
        return view(profile);
    }

    @Transactional
    public ProfileView removeAvatar(UUID profileId, ClientInfo client) {
        UserProfile profile = guard.require(profiles, profileId, AuditObjectType.PROFILE, client);
        String previous = profile.replaceAvatar(null, clock.instant());
        if (previous != null) {
            storage.delete(previous);
            audit.byUser(profile.getOwnerId(), AuditAction.AVATAR_REMOVED, AuditObjectType.PROFILE, profile.getId(),
                    Map.of(), client);
        }
        return view(profile);
    }

    @Transactional(readOnly = true)
    public byte[] avatar(UUID profileId, ClientInfo client) {
        UserProfile profile = guard.require(profiles, profileId, AuditObjectType.PROFILE, client);
        if (profile.getAvatarStorageKey() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return storage.read(profile.getAvatarStorageKey());
    }

    /** Tạo hồ sơ mặc định an toàn khi hai thẻ trình duyệt cùng mở lần đầu: bản ghi trùng bị bỏ qua. */
    private UserProfile createDefault(UUID ownerId) {
        UserProfile fresh = UserProfile.withDefaults(ownerId, clock.instant());
        jdbc.update("""
                insert into user_profiles (id, owner_id, assistant_language, daily_goal_minutes, day_rollover_hour,
                                           created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?) on conflict (owner_id) do nothing""",
                fresh.getId(), ownerId, fresh.getAssistantLanguage(), fresh.getDailyGoalMinutes(),
                fresh.getDayRolloverHour(), Timestamp.from(clock.instant()), Timestamp.from(clock.instant()));
        return profiles.findByOwnerId(ownerId).orElseThrow();
    }

    private static String validDisplayName(String raw) {
        String name = RegistrationService.normalizeDisplayName(raw);
        if (name.isEmpty()) {
            throw new BusinessException(ErrorCode.DISPLAY_NAME_REQUIRED, "displayName");
        }
        if (name.codePointCount(0, name.length()) > DISPLAY_NAME_MAX) {
            throw new BusinessException(ErrorCode.DISPLAY_NAME_TOO_LONG, "displayName",
                    Map.of("max", DISPLAY_NAME_MAX));
        }
        return name;
    }

    /** Chỉ nhận mã múi giờ theo vùng (Asia/Ho_Chi_Minh...), không nhận độ lệch cố định như +07:00. */
    private static String validTimeZone(String raw) {
        String zone = raw.strip();
        if (!ZoneId.getAvailableZoneIds().contains(zone)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_ZONE, "timeZone");
        }
        return zone;
    }

    private UserAccount account(UUID ownerId) {
        return accounts.findById(ownerId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private ProfileView view(UserProfile profile) {
        return view(profile, account(profile.getOwnerId()));
    }

    private static ProfileView view(UserProfile profile, UserAccount account) {
        return new ProfileView(profile, account.getDisplayName(), account.getEmail());
    }

    public record ProfileChanges(String displayName, String timeZone, String assistantLanguage,
                                 Integer dailyGoalMinutes, Integer dayRolloverHour) {
    }

    public record ProfileView(UserProfile profile, String displayName, String email) {

        public boolean hasAvatar() {
            return Objects.nonNull(profile.getAvatarStorageKey());
        }
    }
}
