package com.learningcompanion.appshell.application;

import com.learningcompanion.appshell.domain.UiPreferences;
import com.learningcompanion.appshell.domain.UiPreferencesRepository;
import com.learningcompanion.shared.export.DataExportContributor;
import com.learningcompanion.shared.export.ExportFormat;
import com.learningcompanion.shared.export.ExportWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Phần "Giao diện" của gói xuất (NCL-01-CN-008): chế độ sáng tối, cỡ chữ, trạng thái thanh bên. */
@Component
public class AppearanceExportContributor implements DataExportContributor {

    private final UiPreferencesRepository preferences;

    public AppearanceExportContributor(UiPreferencesRepository preferences) {
        this.preferences = preferences;
    }

    @Override
    public String section() {
        return "appearance";
    }

    @Override
    public String title() {
        return "Giao diện";
    }

    @Override
    public List<String> describe() {
        return List.of("appearance.json: chế độ sáng tối, cỡ chữ và trạng thái thu gọn thanh bên bạn đã chọn.");
    }

    @Override
    public List<ExportFormat> formats() {
        return List.of(ExportFormat.JSON);
    }

    @Override
    public int order() {
        return 25;
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(UUID ownerId, ExportWriter writer) {
        Map<String, Object> data = new LinkedHashMap<>();
        preferences.findByOwnerId(ownerId).ifPresentOrElse(found -> {
            data.put("themeMode", found.getThemeMode().name());
            data.put("fontSize", found.getFontSize().name());
            data.put("sidebarCollapsed", found.isSidebarCollapsed());
            data.put("updatedAt", found.getUpdatedAt());
        }, () -> {
            // Chưa từng chọn: giao diện đang dùng mặc định.
            data.put("themeMode", UiPreferences.DEFAULT_THEME.name());
            data.put("fontSize", UiPreferences.DEFAULT_FONT_SIZE.name());
            data.put("sidebarCollapsed", false);
        });
        writer.json("appearance.json", data);
    }
}
