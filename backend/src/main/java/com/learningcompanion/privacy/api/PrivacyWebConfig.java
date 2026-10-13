package com.learningcompanion.privacy.api;

import com.learningcompanion.shared.web.ApiPaths;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PrivacyWebConfig implements WebMvcConfigurer {

    private final TermsAcceptanceInterceptor termsAcceptance;

    public PrivacyWebConfig(TermsAcceptanceInterceptor termsAcceptance) {
        this.termsAcceptance = termsAcceptance;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(termsAcceptance)
                .addPathPatterns(ApiPaths.V1 + "/**")
                .excludePathPatterns(
                        ApiPaths.AUTH + "/**",
                        ApiPaths.LEGAL + "/**",
                        ApiPaths.DEV + "/**",
                        ApiPaths.FILES + "/**",
                        ApiPaths.ACCOUNTS + "/*/privacy",
                        ApiPaths.ACCOUNTS + "/*/terms/accept",
                        // Người học luôn mang được dữ liệu của mình đi, kể cả khi chưa đồng ý điều khoản mới.
                        ApiPaths.EXPORTS + "/**",
                        // Tên và ảnh đại diện ở đầu trang vẫn hiện trong lúc chờ đồng ý.
                        ApiPaths.PROFILES + "/me",
                        ApiPaths.PROFILES + "/*/avatar");
    }
}
