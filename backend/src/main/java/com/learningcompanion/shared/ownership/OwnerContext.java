package com.learningcompanion.shared.ownership;

import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Chủ sở hữu của yêu cầu hiện tại luôn lấy từ phiên đăng nhập, không bao giờ từ tham số do máy khách gửi.
 * Quản trị viên cũng chỉ là chủ sở hữu dữ liệu của chính mình.
 */
@Component
public class OwnerContext {

    public UUID requireOwnerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.userId();
        }
        throw new BusinessException(ErrorCode.UNAUTHENTICATED);
    }
}
