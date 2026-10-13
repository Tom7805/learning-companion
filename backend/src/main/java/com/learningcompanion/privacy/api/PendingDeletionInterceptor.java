package com.learningcompanion.privacy.api;

import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
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
 * Tài khoản đang chờ xóa chỉ đăng nhập để quyết định có hủy yêu cầu xóa không (NCL-01-CN-009): mọi chức năng khác bị
 * chặn cho tới khi hủy. Đăng nhập, đăng xuất và các điểm về yêu cầu xóa được loại trừ trong {@link PrivacyWebConfig}.
 */
@Component
public class PendingDeletionInterceptor implements HandlerInterceptor {

    private final UserAccountRepository accounts;

    public PendingDeletionInterceptor(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                && accounts.findById(user.userId()).map(UserAccount::isPendingDeletion).orElse(false)) {
            throw new BusinessException(ErrorCode.ACCOUNT_PENDING_DELETION);
        }
        return true;
    }
}
