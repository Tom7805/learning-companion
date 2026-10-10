package com.learningcompanion.shared.mail;

/** Lớp kết nối tới dịch vụ gửi thư giao dịch, thay thế được giữa SMTP thật và bản mô phỏng (QTN-04). */
public interface MailGateway {

    void send(OutgoingMail mail);

    record OutgoingMail(String to, String subject, String html, MailTemplate template) {
    }
}
