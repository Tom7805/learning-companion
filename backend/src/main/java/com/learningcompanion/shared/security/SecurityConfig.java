package com.learningcompanion.shared.security;

import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.web.ApiPaths;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http, SessionResolver sessionResolver,
                                           AppProperties properties,
                                           @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver)
            throws Exception {
        http
                // Cookie XSRF-TOKEN đọc được bằng JavaScript, giao diện gửi lại qua tiêu đề X-XSRF-TOKEN.
                // Phiên được xác minh lại ở mỗi yêu cầu nên không xoay mã CSRF theo "lần đăng nhập",
                // nếu không mã sẽ đổi sau mỗi yêu cầu và các yêu cầu song song bị từ chối.
                .csrf(csrf -> csrf.spa()
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy())
                        .ignoringRequestMatchers(ApiPaths.DEV + "/**"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                ApiPaths.AUTH + "/register",
                                ApiPaths.AUTH + "/login",
                                ApiPaths.AUTH + "/verify-email",
                                ApiPaths.AUTH + "/verify-email/resend",
                                ApiPaths.AUTH + "/sessions/revoke-link").permitAll()
                        .requestMatchers(HttpMethod.GET, ApiPaths.AUTH + "/session", ApiPaths.LEGAL + "/**")
                        .permitAll()
                        .requestMatchers(ApiPaths.DEV + "/**").permitAll()
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> resolver.resolveException(
                                request, response, null, new BusinessException(unauthenticatedCode(request))))
                        .accessDeniedHandler((request, response, ex) -> resolver.resolveException(
                                request, response, null, new BusinessException(ErrorCode.FORBIDDEN))))
                .addFilterBefore(new SessionAuthenticationFilter(sessionResolver,
                        properties.auth().sessionCookieName(), properties.auth().secureCookies()),
                        AnonymousAuthenticationFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), AnonymousAuthenticationFilter.class);
        return http.build();
    }

    /** Cho giao diện biết vì sao cần đăng nhập lại: phiên hết hạn, bị đăng xuất từ xa hay chưa từng đăng nhập. */
    private static ErrorCode unauthenticatedCode(HttpServletRequest request) {
        Object problem = request.getAttribute(SessionAuthenticationFilter.SESSION_PROBLEM);
        if (problem == SessionResolver.Problem.EXPIRED) {
            return ErrorCode.SESSION_EXPIRED;
        }
        if (problem == SessionResolver.Problem.REVOKED) {
            return ErrorCode.SESSION_REVOKED;
        }
        return ErrorCode.UNAUTHENTICATED;
    }

    /** Không dùng đăng nhập theo tên người dùng của Spring, tắt kho người dùng mặc định. */
    @Bean
    public UserDetailsService noUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }

    /** Nạp mã CSRF ngay ở mỗi yêu cầu để cookie luôn có trước lần gửi biểu mẫu đầu tiên. */
    private static final class CsrfCookieFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
