package com.learningcompanion.profile.application;

import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.profile.domain.UserProfile;
import com.learningcompanion.profile.domain.UserProfileRepository;
import com.learningcompanion.shared.export.DataExportContributor;
import com.learningcompanion.shared.export.ExportFormat;
import com.learningcompanion.shared.export.ExportWriter;
import com.learningcompanion.shared.storage.FileStorageService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Phần "Hồ sơ" của gói xuất: tên hiển thị, tùy chọn học tập và ảnh đại diện. */
@Component
public class ProfileExportContributor implements DataExportContributor {

    private final UserProfileRepository profiles;
    private final UserAccountRepository accounts;
    private final FileStorageService storage;

    public ProfileExportContributor(UserProfileRepository profiles, UserAccountRepository accounts,
                                    FileStorageService storage) {
        this.profiles = profiles;
        this.accounts = accounts;
        this.storage = storage;
    }

    @Override
    public String section() {
        return "profile";
    }

    @Override
    public String title() {
        return "Hồ sơ và tùy chọn học tập";
    }

    @Override
    public List<String> describe() {
        return List.of(
                "profile.json: tên hiển thị, múi giờ, ngôn ngữ trợ lý, mục tiêu học mỗi ngày, giờ bắt đầu ngày mới.",
                "avatar.png: ảnh đại diện, nếu bạn đã đặt.");
    }

    @Override
    public List<ExportFormat> formats() {
        return List.of(ExportFormat.JSON, ExportFormat.ORIGINAL);
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(UUID ownerId, ExportWriter writer) {
        UserProfile profile = profiles.findByOwnerId(ownerId).orElse(null);
        Map<String, Object> data = new LinkedHashMap<>();
        accounts.findById(ownerId).ifPresent(account -> data.put("displayName", account.getDisplayName()));
        if (profile == null) {
            // Hồ sơ chỉ được tạo khi người học mở lần đầu; chưa mở thì mọi tùy chọn đang là mặc định.
            data.put("timeZone", null);
            data.put("assistantLanguage", UserProfile.DEFAULT_LANGUAGE);
            data.put("dailyGoalMinutes", UserProfile.DEFAULT_DAILY_GOAL);
            data.put("dayRolloverHour", UserProfile.DEFAULT_ROLLOVER_HOUR);
            writer.json("profile.json", data);
            return;
        }
        data.put("timeZone", profile.getTimeZone());
        data.put("assistantLanguage", profile.getAssistantLanguage());
        data.put("dailyGoalMinutes", profile.getDailyGoalMinutes());
        data.put("dayRolloverHour", profile.getDayRolloverHour());
        data.put("hasAvatar", profile.getAvatarStorageKey() != null);
        data.put("updatedAt", profile.getUpdatedAt());
        writer.json("profile.json", data);
        if (profile.getAvatarStorageKey() != null) {
            writer.bytes("avatar.png", storage.read(profile.getAvatarStorageKey()));
        }
    }
}
