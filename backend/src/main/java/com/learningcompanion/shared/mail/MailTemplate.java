package com.learningcompanion.shared.mail;

public enum MailTemplate {
    VERIFY_EMAIL("email/verify-email", "Xác thực địa chỉ thư của bạn", false),
    REGISTRATION_ATTEMPT("email/registration-attempt", "Có người vừa thử đăng ký bằng địa chỉ thư của bạn", false),
    NEW_DEVICE_LOGIN("email/new-device-login", "Tài khoản của bạn vừa đăng nhập trên thiết bị mới", false),
    ACCOUNT_LOCKED("email/account-locked", "Đăng nhập tạm khóa do nhập sai mật khẩu nhiều lần", false),
    /** Người học chờ xóa quên mật khẩu vẫn cần đặt lại được để vào hủy yêu cầu xóa. */
    PASSWORD_RESET("email/password-reset", "Đặt lại mật khẩu Learning Companion", true),
    PASSWORD_CHANGED("email/password-changed", "Mật khẩu tài khoản của bạn vừa được thay đổi", false),
    PRODUCT_NEWS("email/product-news", "Có gì mới ở Learning Companion", false),
    DATA_EXPORT_READY("email/data-export-ready", "Gói dữ liệu của bạn đã sẵn sàng để tải", false),
    ACCOUNT_DELETION_SCHEDULED("email/account-deletion-scheduled",
            "Tài khoản của bạn sẽ bị xóa sau 30 ngày", true),
    ACCOUNT_DELETION_CANCELLED("email/account-deletion-cancelled", "Đã hủy yêu cầu xóa tài khoản", true),
    ACCOUNT_DELETION_ATTEMPT("email/account-deletion-attempt",
            "Một yêu cầu xóa tài khoản của bạn vừa bị từ chối", true),
    ACCOUNT_DELETED("email/account-deleted", "Tài khoản Learning Companion của bạn đã được xóa", true);

    private final String view;
    private final String subject;
    private final boolean deliveredWhilePendingDeletion;

    MailTemplate(String view, String subject, boolean deliveredWhilePendingDeletion) {
        this.view = view;
        this.subject = subject;
        this.deliveredWhilePendingDeletion = deliveredWhilePendingDeletion;
    }

    public String view() {
        return view;
    }

    public String subject() {
        return subject;
    }

    /**
     * Thư vẫn gửi khi tài khoản đang chờ xóa (NCL-01-CN-009): chỉ thư về chính yêu cầu xóa và thư người học tự
     * yêu cầu để lấy lại quyền đăng nhập; mọi thông báo khác dừng lại.
     */
    public boolean deliveredWhilePendingDeletion() {
        return deliveredWhilePendingDeletion;
    }
}
