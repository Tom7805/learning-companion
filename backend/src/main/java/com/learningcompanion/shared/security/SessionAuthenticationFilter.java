package com.learningcompanion.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Đọc cookie phiên, xác minh với cơ sở dữ liệu rồi đặt người dùng vào SecurityContext.
 * Phiên hết hạn hoặc bị thu hồi thì xóa cookie và ghi lý do vào yêu cầu để phản hồi báo đúng cho giao diện.
 */
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    /** Thuộc tính yêu cầu chứa {@link SessionResolver.Problem} khi cookie phiên không còn dùng được. */
    public static final String SESSION_PROBLEM = SessionAuthenticationFilter.class.getName() + ".problem";

    private final SessionResolver resolver;
    private final String cookieName;
    private final boolean secureCookies;

    public SessionAuthenticationFilter(SessionResolver resolver, String cookieName, boolean secureCookies) {
        this.resolver = resolver;
        this.cookieName = cookieName;
        this.secureCookies = secureCookies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = readCookie(request);
        if (token != null) {
            SessionResolver.SessionLookup lookup = resolver.resolve(token);
            if (lookup.user() != null) {
                AuthenticatedUser user = lookup.user();
                var authentication = new UsernamePasswordAuthenticationToken(
                        user, null, List.of(new SimpleGrantedAuthority(user.role().authority())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                request.setAttribute(SESSION_PROBLEM, lookup.problem());
                response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(cookieName, "")
                        .httpOnly(true).secure(secureCookies).sameSite("Lax").path("/").maxAge(Duration.ZERO)
                        .build().toString());
            }
        }
        chain.doFilter(request, response);
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
