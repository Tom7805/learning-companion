package com.learningcompanion.shared.mail;

public enum MailTemplate {
    VERIFY_EMAIL("email/verify-email", "Xác thực địa chỉ thư của bạn"),
    REGISTRATION_ATTEMPT("email/registration-attempt", "Có người vừa thử đăng ký bằng địa chỉ thư của bạn"),
    NEW_DEVICE_LOGIN("email/new-device-login", "Tài khoản của bạn vừa đăng nhập trên thiết bị mới"),
    ACCOUNT_LOCKED("email/account-locked", "Đăng nhập tạm khóa do nhập sai mật khẩu nhiều lần");

    private final String view;
    private final String subject;

    MailTemplate(String view, String subject) {
        this.view = view;
        this.subject = subject;
    }

    public String view() {
        return view;
    }

    public String subject() {
        return subject;
    }
}
