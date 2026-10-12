package com.learningcompanion.privacy.api;

import com.learningcompanion.privacy.application.ConsentService;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Điều khoản hoặc chính sách lên phiên bản mới mà người học chưa đồng ý thì chặn mọi chức năng (TC-03).
 * Dữ liệu vẫn giữ nguyên; người học quay lại đồng ý là dùng tiếp. Các đường dẫn để đọc và đồng ý
 * văn bản mới, đăng nhập và đăng xuất được loại trừ trong {@link PrivacyWebConfig}.
 */
@Component
public class TermsAcceptanceInterceptor implements HandlerInterceptor {

    private final ConsentService consents;

    public TermsAcceptanceInterceptor(ConsentService consents) {
        this.consents = consents;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                && consents.requiresAcceptance(user.userId())) {
            throw new BusinessException(ErrorCode.TERMS_ACCEPTANCE_REQUIRED);
        }
        return true;
    }
}
