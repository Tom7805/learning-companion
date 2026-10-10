package com.learningcompanion.auth.api;

import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.security.SessionTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie HttpOnly để JavaScript không đọc được.
 * Phiên không ghi nhớ: không đặt hạn nên tự xóa khi đóng trình duyệt. Phiên ghi nhớ: giữ 30 ngày.
 * Cookie thiết bị giữ lâu dài, chỉ để nhận ra trình duyệt đã từng đăng nhập.
 */
@Component
public class SessionCookies {

    private static final Duration DEVICE_COOKIE_LIFETIME = Duration.ofDays(400);

    private final AppProperties.Auth settings;
    private final SessionTokenService tokens;

    public SessionCookies(AppProperties properties, SessionTokenService tokens) {
        this.settings = properties.auth();
        this.tokens = tokens;
    }

    public ResponseCookie issue(String rawToken, boolean rememberDevice, Duration lifetime) {
        ResponseCookie.ResponseCookieBuilder builder = base(settings.sessionCookieName(), rawToken);
        if (rememberDevice) {
            builder.maxAge(lifetime);
        }
        return builder.build();
    }

    public ResponseCookie clear() {
        return base(settings.sessionCookieName(), "").maxAge(Duration.ZERO).build();
    }

    /** Mã thiết bị hiện có, hoặc mã mới nếu trình duyệt chưa có. */
    public DeviceCookie device(HttpServletRequest request) {
        Optional<String> existing = Optional.ofNullable(request.getCookies())
                .flatMap(cookies -> Arrays.stream(cookies)
                        .filter(cookie -> settings.deviceCookieName().equals(cookie.getName()))
                        .map(Cookie::getValue)
                        .filter(value -> !value.isBlank() && value.length() <= 128)
                        .findFirst());
        String id = existing.orElseGet(tokens::newToken);
        return new DeviceCookie(id, base(settings.deviceCookieName(), id).maxAge(DEVICE_COOKIE_LIFETIME).build());
    }

    private ResponseCookie.ResponseCookieBuilder base(String name, String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(settings.secureCookies())
                .sameSite("Lax")
                .path("/");
    }

    public record DeviceCookie(String id, ResponseCookie cookie) {
    }
}
