package com.learningcompanion.profile.domain;

import com.learningcompanion.shared.ownership.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Tùy chọn học tập cá nhân của một người học (NCL-01-CN-005). Tên hiển thị nằm ở tài khoản. */
@Entity
@Table(name = "user_profiles")
public class UserProfile implements OwnedEntity {

    public static final String DEFAULT_LANGUAGE = "vi";
    public static final int DEFAULT_DAILY_GOAL = 30;
    public static final int DEFAULT_ROLLOVER_HOUR = 4;

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "time_zone", length = 64)
    private String timeZone;

    @Column(name = "assistant_language", nullable = false, length = 8)
    private String assistantLanguage;

    @Column(name = "daily_goal_minutes", nullable = false)
    private int dailyGoalMinutes;

    @Column(name = "day_rollover_hour", nullable = false)
    private int dayRolloverHour;

    @Column(name = "avatar_storage_key", length = 100)
    private String avatarStorageKey;

    @Column(name = "avatar_updated_at")
    private Instant avatarUpdatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected UserProfile() {
    }

    public static UserProfile withDefaults(UUID ownerId, Instant now) {
        UserProfile profile = new UserProfile();
        profile.id = UUID.randomUUID();
        profile.ownerId = ownerId;
        profile.assistantLanguage = DEFAULT_LANGUAGE;
        profile.dailyGoalMinutes = DEFAULT_DAILY_GOAL;
        profile.dayRolloverHour = DEFAULT_ROLLOVER_HOUR;
        profile.createdAt = now;
        profile.updatedAt = now;
        return profile;
    }

    public void changeTimeZone(String zone, Instant now) {
        timeZone = zone;
        updatedAt = now;
    }

    public void changeAssistantLanguage(String language, Instant now) {
        assistantLanguage = language;
        updatedAt = now;
    }

    public void changeDailyGoal(int minutes, Instant now) {
        dailyGoalMinutes = minutes;
        updatedAt = now;
    }

    public void changeDayRolloverHour(int hour, Instant now) {
        dayRolloverHour = hour;
        updatedAt = now;
    }

    /** Trả về khóa ảnh cũ (nếu có) để xóa khỏi kho tệp sau khi lưu ảnh mới. */
    public String replaceAvatar(String storageKey, Instant now) {
        String previous = avatarStorageKey;
        avatarStorageKey = storageKey;
        avatarUpdatedAt = storageKey == null ? null : now;
        updatedAt = now;
        return previous;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getOwnerId() {
        return ownerId;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public String getAssistantLanguage() {
        return assistantLanguage;
    }

    public int getDailyGoalMinutes() {
        return dailyGoalMinutes;
    }

    public int getDayRolloverHour() {
        return dayRolloverHour;
    }

    public String getAvatarStorageKey() {
        return avatarStorageKey;
    }

    public Instant getAvatarUpdatedAt() {
        return avatarUpdatedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
