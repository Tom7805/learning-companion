package com.learningcompanion.profile.api.dto;

import com.learningcompanion.profile.application.ProfileService;
import com.learningcompanion.profile.domain.UserProfile;
import com.learningcompanion.shared.web.ApiPaths;
import java.time.Instant;
import java.util.UUID;

/**
 * Hồ sơ của chính người học. {@code avatarUrl} kèm mốc cập nhật để trình duyệt tải ảnh mới
 * ngay khi đổi mà vẫn lưu đệm được ảnh cũ.
 */
public record ProfileResponse(UUID id, String displayName, String email, String timeZone, String assistantLanguage,
                              int dailyGoalMinutes, int dayRolloverHour, String avatarUrl, Instant updatedAt) {

    public static ProfileResponse of(ProfileService.ProfileView view) {
        UserProfile profile = view.profile();
        String avatarUrl = view.hasAvatar()
                ? ApiPaths.PROFILES + "/" + profile.getId() + "/avatar?v=" + profile.getAvatarUpdatedAt().toEpochMilli()
                : null;
        return new ProfileResponse(profile.getId(), view.displayName(), view.email(), profile.getTimeZone(),
                profile.getAssistantLanguage(), profile.getDailyGoalMinutes(), profile.getDayRolloverHour(), avatarUrl,
                profile.getUpdatedAt());
    }
}
