package com.learningcompanion.appshell.domain;

import com.learningcompanion.shared.ownership.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Tùy chọn giao diện của một người học (NCL-02-CN-001). */
@Entity
@Table(name = "ui_preferences")
public class UiPreferences implements OwnedEntity {

    /** Ba cỡ chữ của toàn giao diện. */
    public enum FontSize { SMALL, MEDIUM, LARGE }

    public static final ThemeMode DEFAULT_THEME = ThemeMode.SYSTEM;
    public static final FontSize DEFAULT_FONT_SIZE = FontSize.MEDIUM;

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme_mode", nullable = false, length = 10)
    private ThemeMode themeMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "font_size", nullable = false, length = 10)
    private FontSize fontSize;

    @Column(name = "sidebar_collapsed", nullable = false)
    private boolean sidebarCollapsed;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected UiPreferences() {
    }

    public static UiPreferences withDefaults(UUID ownerId, Instant now) {
        UiPreferences preferences = new UiPreferences();
        preferences.id = UUID.randomUUID();
        preferences.ownerId = ownerId;
        preferences.themeMode = DEFAULT_THEME;
        preferences.fontSize = DEFAULT_FONT_SIZE;
        preferences.updatedAt = now;
        return preferences;
    }

    public void change(ThemeMode theme, FontSize font, Boolean collapsed, Instant now) {
        if (theme != null) {
            themeMode = theme;
        }
        if (font != null) {
            fontSize = font;
        }
        if (collapsed != null) {
            sidebarCollapsed = collapsed;
        }
        updatedAt = now;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getOwnerId() {
        return ownerId;
    }

    public ThemeMode getThemeMode() {
        return themeMode;
    }

    public FontSize getFontSize() {
        return fontSize;
    }

    public boolean isSidebarCollapsed() {
        return sidebarCollapsed;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
