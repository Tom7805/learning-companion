package com.learningcompanion.auth.application;

import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.mail.MailService;
import com.learningcompanion.shared.mail.MailTemplate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

/** Chỉ gửi thư sau khi giao dịch đã ghi xong, tránh gửi đường dẫn cho bản ghi bị hủy. */
@Component
public class AuthMailNotifier {

    // Múi giờ của người học chưa có trước khi đăng ký xong, dùng giờ Việt Nam mặc định.
    private static final DateTimeFormatter ATTEMPT_TIME =
            DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final MailService mailService;
    private final AppProperties properties;

    public AuthMailNotifier(MailService mailService, AppProperties properties) {
        this.mailService = mailService;
        this.properties = properties;
    }

    @TransactionalEventListener
    public void on(VerificationMailRequested event) {
        String verifyUrl = UriComponentsBuilder.fromUriString(properties.frontendUrl())
                .path("/verify-email")
                .queryParam("token", event.rawToken())
                .build()
                .toUriString();
        mailService.send(event.email(), MailTemplate.VERIFY_EMAIL, Map.of(
                "displayName", event.displayName(),
                "verifyUrl", verifyUrl,
                "ttlHours", properties.auth().verificationTokenTtl().toHours()));
    }

    @TransactionalEventListener
    public void on(RegistrationAttemptNoticeRequested event) {
        mailService.send(event.email(), MailTemplate.REGISTRATION_ATTEMPT, Map.of(
                "displayName", event.displayName(),
                "attemptedAt", ATTEMPT_TIME.format(event.attemptedAt()),
                "loginUrl", properties.frontendUrl() + "/login"));
    }

    public record VerificationMailRequested(String email, String displayName, String rawToken) {
    }

    public record RegistrationAttemptNoticeRequested(String email, String displayName, Instant attemptedAt) {
    }
}
