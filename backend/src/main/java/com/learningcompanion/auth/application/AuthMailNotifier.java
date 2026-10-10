package com.learningcompanion.auth.application;

import com.learningcompanion.auth.domain.DeviceInfo;
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

    // Múi giờ của người học có ở hồ sơ (story sau); hiện dùng giờ Việt Nam mặc định.
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final MailService mailService;
    private final AppProperties properties;

    public AuthMailNotifier(MailService mailService, AppProperties properties) {
        this.mailService = mailService;
        this.properties = properties;
    }

    @TransactionalEventListener
    public void on(VerificationMailRequested event) {
        mailService.send(event.email(), MailTemplate.VERIFY_EMAIL, Map.of(
                "displayName", event.displayName(),
                "verifyUrl", frontendUrl("/verify-email", event.rawToken()),
                "ttlHours", properties.auth().verificationTokenTtl().toHours()));
    }

    @TransactionalEventListener
    public void on(RegistrationAttemptNoticeRequested event) {
        mailService.send(event.email(), MailTemplate.REGISTRATION_ATTEMPT, Map.of(
                "displayName", event.displayName(),
                "attemptedAt", TIME.format(event.attemptedAt()),
                "loginUrl", properties.frontendUrl() + "/login"));
    }

    @TransactionalEventListener
    public void on(NewDeviceLoginAlert event) {
        DeviceInfo device = event.device();
        String location = device.location() != null ? device.location()
                : device.localNetwork() ? "Mạng nội bộ" : "Không xác định được";
        mailService.send(event.email(), MailTemplate.NEW_DEVICE_LOGIN, Map.of(
                "displayName", event.displayName(),
                "device", device.label(),
                "location", location,
                "signedInAt", TIME.format(event.signedInAt()),
                "revokeUrl", frontendUrl("/devices/revoke", event.rawRevokeToken())));
    }

    @TransactionalEventListener
    public void on(AccountLockedNotice event) {
        mailService.send(event.email(), MailTemplate.ACCOUNT_LOCKED, Map.of(
                "displayName", event.displayName(),
                "lockedUntil", TIME.format(event.lockedUntil()),
                "lockoutMinutes", properties.auth().lockoutDuration().toMinutes()));
    }

    private String frontendUrl(String path, String token) {
        return UriComponentsBuilder.fromUriString(properties.frontendUrl())
                .path(path)
                .queryParam("token", token)
                .build()
                .toUriString();
    }

    public record VerificationMailRequested(String email, String displayName, String rawToken) {
    }

    public record RegistrationAttemptNoticeRequested(String email, String displayName, Instant attemptedAt) {
    }

    public record NewDeviceLoginAlert(String email, String displayName, DeviceInfo device, Instant signedInAt,
                                      String rawRevokeToken) {
    }

    public record AccountLockedNotice(String email, String displayName, Instant lockedUntil) {
    }
}
