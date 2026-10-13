package com.learningcompanion.shared.mail;

/**
 * Quy tắc quyết định một thư có được gửi tới người nhận hay không, xét ngay lúc gửi. Ví dụ tài khoản đang chờ xóa
 * chỉ nhận thư về yêu cầu xóa (NCL-01-CN-009).
 */
public interface MailRecipientPolicy {

    boolean allows(String to, MailTemplate template);
}
