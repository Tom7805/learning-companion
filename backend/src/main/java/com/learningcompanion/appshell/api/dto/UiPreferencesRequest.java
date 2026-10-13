package com.learningcompanion.appshell.api.dto;

/** Đổi một hoặc vài tùy chọn giao diện; trường để trống giữ nguyên. */
public record UiPreferencesRequest(String themeMode, String fontSize, Boolean sidebarCollapsed) {
}
