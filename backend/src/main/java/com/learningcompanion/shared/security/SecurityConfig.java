package com.learningcompanion.shared.security;

import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.ownership.AccessDeniedLogger;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
public class SecurityConfig {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http, SessionResolver sessionResolver,
                                           AppProperties properties, AccessDeniedLogger deniedLogger,
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
                        // Lượt xử lý tiếp của yêu cầu bất đồng bộ (kênh thông báo SSE) thuộc về yêu cầu gốc đã được
                        // xác thực; phiên không lưu trong bộ nhớ nên không kiểm lại được ở lượt này.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers(HttpMethod.POST,
                                ApiPaths.AUTH + "/register",
                                ApiPaths.AUTH + "/login",
                                ApiPaths.AUTH + "/verify-email",
                                ApiPaths.AUTH + "/verify-email/resend",
                                ApiPaths.AUTH + "/sessions/revoke-link",
                                ApiPaths.AUTH + "/password/forgot",
                                ApiPaths.AUTH + "/password/reset/check",
                                ApiPaths.AUTH + "/password/reset",
                                // Đăng nhập qua nhà cung cấp định danh; liên kết từ cài đặt cần phiên, kiểm trong dịch vụ.
                                ApiPaths.AUTH + "/oauth/*/start",
                                ApiPaths.AUTH + "/oauth/*/callback",
                                ApiPaths.AUTH + "/oauth/signup",
                                ApiPaths.AUTH + "/oauth/link").permitAll()
                        .requestMatchers(HttpMethod.GET, ApiPaths.AUTH + "/session", ApiPaths.AUTH + "/oauth/providers",
                                ApiPaths.LEGAL + "/**")
                        .permitAll()
                        // Đường dẫn ký tự nó là quyền mở tệp, có hạn năm phút (NCL-01-CN-004).
                        .requestMatchers(HttpMethod.GET, ApiPaths.FILES + "/*").permitAll()
                        // Gói dữ liệu xuất: chỉ cấp đường dẫn ký sau khi người học xác nhận lại danh tính (NCL-01-CN-008).
                        .requestMatchers(HttpMethod.GET, ApiPaths.EXPORTS + "/files/*").permitAll()
                        .requestMatchers(ApiPaths.ADMIN + "/**").hasRole("ADMIN")
                        .requestMatchers(ApiPaths.DEV + "/**").permitAll()
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> resolver.resolveException(
                                request, response, null, new BusinessException(unauthenticatedCode(request))))
                        // Lỗi mã chống giả mạo có mã riêng: thường chỉ do hai yêu cầu song song cùng nhận mã mới
                        // khi trình duyệt chưa có cookie, giao diện gửi lại một lần là xong.
                        .accessDeniedHandler((request, response, ex) -> {
                            if (ex instanceof CsrfException) {
                                resolver.resolveException(request, response, null,
                                        new BusinessException(ErrorCode.CSRF_TOKEN_INVALID));
                                return;
                            }
                            // Đã đăng nhập mà sai vai trò (người học mở khu quản trị): ghi nhật ký lần từ chối.
                            if (SecurityContextHolder.getContext().getAuthentication() != null
                                    && SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                                    instanceof AuthenticatedUser user) {
                                try {
                                    deniedLogger.wrongRole(user.userId(), request.getMethod(),
                                            request.getRequestURI(), ClientInfo.from(request));
                                } catch (RuntimeException failure) {
                                    // Vẫn từ chối dù không ghi được nhật ký; lỗi ghi được báo trong nhật ký máy chủ.
                                    LOG.error("Could not audit a role denial", failure);
                                }
                            }
                            resolver.resolveException(request, response, null,
                                    new BusinessException(ErrorCode.FORBIDDEN));
                        }))
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
