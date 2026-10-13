package com.learningcompanion.appshell.api;

import com.learningcompanion.appshell.api.dto.UiPreferencesRequest;
import com.learningcompanion.appshell.application.UiPreferencesService;
import com.learningcompanion.shared.web.ApiPaths;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tùy chọn giao diện của chính người học đang đăng nhập; không có mã trên đường dẫn nên không đọc được của ai khác. */
@RestController
@RequestMapping(ApiPaths.UI_PREFERENCES)
public class UiPreferencesController {

    private final UiPreferencesService preferences;

    public UiPreferencesController(UiPreferencesService preferences) {
        this.preferences = preferences;
    }

    @GetMapping
    public UiPreferencesService.View mine() {
        return preferences.mine();
    }

    @PatchMapping
    public UiPreferencesService.View update(@RequestBody UiPreferencesRequest request) {
        return preferences.update(request.themeMode(), request.fontSize(), request.sidebarCollapsed());
    }
}
