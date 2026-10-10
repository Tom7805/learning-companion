package com.learningcompanion.auth.api.dto;

/**
 * Trạng thái đăng nhập của trình duyệt. Khi chưa đăng nhập, `reason` cho biết phiên vừa hết hạn
 * (SESSION_EXPIRED) hay bị đăng xuất từ thiết bị khác (SESSION_REVOKED), để giao diện báo đúng.
 */
public record SessionStatusResponse(boolean authenticated, AccountResponse account, String reason) {

    public SessionStatusResponse(boolean authenticated, AccountResponse account) {
        this(authenticated, account, null);
    }

    public static SessionStatusResponse anonymous() {
        return new SessionStatusResponse(false, null, null);
    }
}
