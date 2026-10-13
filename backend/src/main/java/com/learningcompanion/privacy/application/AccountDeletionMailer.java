package com.learningcompanion.privacy.application;

import com.learningcompanion.auth.domain.DeviceInfo;
import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.mail.MailService;
import com.learningcompanion.shared.mail.MailTemplate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Thư về yêu cầu xóa tài khoản (NCL-01-CN-009), chỉ gửi sau khi giao dịch tương ứng đã ghi xong. */
@Component
public class AccountDeletionMailer {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");

    private final MailService mail;
    private final AppProperties properties;

    public AccountDeletionMailer(MailService mail, AppProperties properties) {
        this.mail = mail;
        this.properties = properties;
    }

    @TransactionalEventListener
    public void on(DeletionScheduled event) {
        mail.send(event.email(), MailTemplate.ACCOUNT_DELETION_SCHEDULED, Map.of(
                "displayName", event.displayName(),
                "deleteAt", TIME.withZone(event.zone()).format(event.deleteAt()),
                "graceDays", AccountDeletionService.GRACE_PERIOD.toDays(),
                "loginUrl", properties.frontendUrl() + "/login"));
    }

    @TransactionalEventListener
    public void on(DeletionCancelled event) {
        mail.send(event.email(), MailTemplate.ACCOUNT_DELETION_CANCELLED, Map.of(
                "displayName", event.displayName(),
                "securityUrl", properties.frontendUrl() + "/security"));
    }

    @TransactionalEventListener
    public void on(DeletionAttemptRejected event) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("displayName", event.displayName());
        variables.put("attemptedAt", TIME.withZone(event.zone()).format(event.attemptedAt()));
        variables.put("device", event.device() == null ? "Không xác định được" : event.device().label());
        variables.put("location", event.device() == null || event.device().location() == null
                ? "Không xác định được" : event.device().location());
        variables.put("devicesUrl", properties.frontendUrl() + "/devices");
        variables.put("securityUrl", properties.frontendUrl() + "/security");
        mail.send(event.email(), MailTemplate.ACCOUNT_DELETION_ATTEMPT, variables);
    }

    @TransactionalEventListener
    public void on(AccountDeleted event) {
        mail.send(event.email(), MailTemplate.ACCOUNT_DELETED, Map.of(
                "displayName", event.displayName(),
                "registerUrl", properties.frontendUrl() + "/register"));
    }

    public record DeletionScheduled(String email, String displayName, Instant deleteAt, ZoneId zone) {
    }

    public record DeletionCancelled(String email, String displayName) {
    }

    public record DeletionAttemptRejected(String email, String displayName, Instant attemptedAt, DeviceInfo device,
                                          ZoneId zone) {
    }

    public record AccountDeleted(String email, String displayName) {
    }
}
