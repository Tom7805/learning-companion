package com.learningcompanion.privacy.api;

import com.learningcompanion.shared.web.ApiPaths;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PrivacyWebConfig implements WebMvcConfigurer {

    private final PendingDeletionInterceptor pendingDeletion;
    private final TermsAcceptanceInterceptor termsAcceptance;

    public PrivacyWebConfig(PendingDeletionInterceptor pendingDeletion, TermsAcceptanceInterceptor termsAcceptance) {
        this.pendingDeletion = pendingDeletion;
        this.termsAcceptance = termsAcceptance;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Tài khoản chờ xóa chỉ được đăng nhập, đăng xuất và quyết định về yêu cầu xóa (NCL-01-CN-009).
        registry.addInterceptor(pendingDeletion)
                .addPathPatterns(ApiPaths.V1 + "/**")
                .excludePathPatterns(
                        ApiPaths.AUTH + "/**",
                        ApiPaths.LEGAL + "/**",
                        ApiPaths.DEV + "/**",
                        ApiPaths.ACCOUNTS + "/*/deletion")
                .order(0);
        registry.addInterceptor(termsAcceptance)
                .addPathPatterns(ApiPaths.V1 + "/**")
                .excludePathPatterns(
                        ApiPaths.AUTH + "/**",
                        ApiPaths.LEGAL + "/**",
                        ApiPaths.DEV + "/**",
                        ApiPaths.FILES + "/**",
                        ApiPaths.ACCOUNTS + "/*/privacy",
                        ApiPaths.ACCOUNTS + "/*/terms/accept",
                        ApiPaths.ACCOUNTS + "/*/deletion",
                        // Người học luôn mang được dữ liệu của mình đi, kể cả khi chưa đồng ý điều khoản mới.
                        ApiPaths.EXPORTS + "/**",
                        // Tên và ảnh đại diện ở đầu trang vẫn hiện trong lúc chờ đồng ý.
                        ApiPaths.PROFILES + "/me",
                        ApiPaths.PROFILES + "/*/avatar");
    }
}
