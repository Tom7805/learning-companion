package com.learningcompanion.profile.application;

import com.learningcompanion.profile.domain.UserProfileRepository;
import com.learningcompanion.shared.erasure.PersonalDataEraser;
import java.util.List;
import org.springframework.stereotype.Component;

/** Hồ sơ khi xóa tài khoản: ảnh đại diện bị xóa khỏi kho; dòng hồ sơ mất theo khóa ngoại tới tài khoản. */
@Component
public class ProfileDataEraser implements PersonalDataEraser {

    private final UserProfileRepository profiles;

    public ProfileDataEraser(UserProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Override
    public String section() {
        return "profile";
    }

    @Override
    public int erase(Subject subject, List<String> filesToDelete) {
        return profiles.findByOwnerId(subject.accountId())
                .map(profile -> {
                    if (profile.getAvatarStorageKey() != null) {
                        filesToDelete.add(profile.getAvatarStorageKey());
                    }
                    return 1;
                })
                .orElse(0);
    }
}
