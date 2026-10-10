package com.learningcompanion.auth.api;

import com.learningcompanion.shared.config.AppProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Cookie phiên HttpOnly để JavaScript không đọc được; không đặt hạn nên tự xóa khi đóng trình duyệt. */
@Component
public class SessionCookies {

    private final AppProperties.Auth settings;

    public SessionCookies(AppProperties properties) {
        this.settings = properties.auth();
    }

    public ResponseCookie issue(String rawToken) {
        return base(rawToken).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(settings.sessionCookieName(), value)
                .httpOnly(true)
                .secure(settings.secureCookies())
                .sameSite("Lax")
                .path("/");
    }
}
