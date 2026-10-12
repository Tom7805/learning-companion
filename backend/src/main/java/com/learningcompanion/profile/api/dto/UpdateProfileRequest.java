package com.learningcompanion.profile.api.dto;

import com.learningcompanion.profile.application.ProfileService;

/** Mọi trường đều không bắt buộc: chỉ trường có giá trị mới được đổi. */
public record UpdateProfileRequest(String displayName, String timeZone, String assistantLanguage,
                                   Integer dailyGoalMinutes, Integer dayRolloverHour) {

    public ProfileService.ProfileChanges toChanges() {
        return new ProfileService.ProfileChanges(displayName, timeZone, assistantLanguage, dailyGoalMinutes,
                dayRolloverHour);
    }
}
