package com.learningcompanion.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/**
 * Thông tin máy khách dùng cho nhật ký, giới hạn tần suất và phiên đăng nhập.
 * `locationHint` lấy từ tiêu đề vị trí mà proxy phía trước (ví dụ Cloudflare) gắn vào, nếu có.
 */
public record ClientInfo(String ipAddress, String userAgent, String locationHint) {

    private static final int MAX_USER_AGENT = 512;
    private static final int MAX_LOCATION = 120;

    public ClientInfo(String ipAddress, String userAgent) {
        this(ipAddress, userAgent, null);
    }

    public static ClientInfo from(HttpServletRequest request) {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        if (userAgent != null && userAgent.length() > MAX_USER_AGENT) {
            userAgent = userAgent.substring(0, MAX_USER_AGENT);
        }
        return new ClientInfo(request.getRemoteAddr(), userAgent, locationFrom(request));
    }

    private static String locationFrom(HttpServletRequest request) {
        String city = firstNonBlank(request.getHeader("X-Geo-City"), request.getHeader("CF-IPCity"));
        String country = firstNonBlank(request.getHeader("X-Geo-Country"), request.getHeader("CF-IPCountry"));
        String location = city != null && country != null ? city + ", " + country : firstNonBlank(city, country);
        return location == null || location.length() <= MAX_LOCATION ? location : location.substring(0, MAX_LOCATION);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.strip();
        }
        return second != null && !second.isBlank() ? second.strip() : null;
    }
}
