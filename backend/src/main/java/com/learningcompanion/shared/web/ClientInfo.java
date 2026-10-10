package com.learningcompanion.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/** Thông tin máy khách dùng cho nhật ký, giới hạn tần suất và phiên đăng nhập. */
public record ClientInfo(String ipAddress, String userAgent) {

    private static final int MAX_USER_AGENT = 512;

    public static ClientInfo from(HttpServletRequest request) {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        if (userAgent != null && userAgent.length() > MAX_USER_AGENT) {
            userAgent = userAgent.substring(0, MAX_USER_AGENT);
        }
        return new ClientInfo(request.getRemoteAddr(), userAgent);
    }
}
