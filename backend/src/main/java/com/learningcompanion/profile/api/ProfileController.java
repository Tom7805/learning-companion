package com.learningcompanion.profile.api;

import com.learningcompanion.profile.api.dto.ProfileResponse;
import com.learningcompanion.profile.api.dto.UpdateProfileRequest;
import com.learningcompanion.profile.application.ProfileService;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Hồ sơ người học. Hồ sơ của người khác luôn được trả lời 404 (TC-05). */
@RestController
@RequestMapping(ApiPaths.PROFILES)
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/me")
    public ProfileResponse me() {
        return ProfileResponse.of(profiles.me());
    }

    @PatchMapping("/{id}")
    public ProfileResponse update(@PathVariable UUID id, @RequestBody UpdateProfileRequest request,
                                  HttpServletRequest http) {
        return ProfileResponse.of(profiles.update(id, request.toChanges(), ClientInfo.from(http)));
    }

    @PutMapping(path = "/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileResponse replaceAvatar(@PathVariable UUID id, @RequestParam("file") MultipartFile file,
                                         HttpServletRequest http) throws IOException {
        return ProfileResponse.of(profiles.replaceAvatar(id, file.getBytes(), ClientInfo.from(http)));
    }

    @DeleteMapping("/{id}/avatar")
    public ProfileResponse removeAvatar(@PathVariable UUID id, HttpServletRequest http) {
        return ProfileResponse.of(profiles.removeAvatar(id, ClientInfo.from(http)));
    }

    /** Địa chỉ ảnh đổi theo mốc cập nhật nên lưu đệm lâu được; chỉ chủ sở hữu mở được. */
    @GetMapping("/{id}/avatar")
    public ResponseEntity<byte[]> avatar(@PathVariable UUID id, HttpServletRequest http) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(profiles.avatar(id, ClientInfo.from(http)));
    }
}
