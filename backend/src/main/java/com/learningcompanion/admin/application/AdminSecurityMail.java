package com.learningcompanion.admin.application;

import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.mail.MailService;
import com.learningcompanion.shared.mail.MailTemplate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Thư cảnh báo về đăng nhập quản trị; chỉ gửi sau khi giao dịch ghi xong. */
@Component
public class AdminSecurityMail {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final MailService mailService;
    private final AppProperties.Admin settings;

    public AdminSecurityMail(MailService mailService, AppProperties properties) {
        this.mailService = mailService;
        this.settings = properties.admin();
    }

    @TransactionalEventListener
    public void on(AdminLoginLockedNotice event) {
        mailService.send(event.email(), MailTemplate.ADMIN_LOGIN_LOCKED, Map.of(
                "displayName", event.displayName(),
                "lockedUntil", TIME.format(event.lockedUntil()),
                "lockoutMinutes", settings.lockoutDuration().toMinutes(),
                "failedCodes", event.failedCodes(),
                "ipAddress", event.ipAddress() == null ? "không rõ" : event.ipAddress()));
    }

    public record AdminLoginLockedNotice(String email, String displayName, Instant lockedUntil, int failedCodes,
                                         String ipAddress) {
    }
}
