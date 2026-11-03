package com.learningcompanion.ai.gateway;

import com.learningcompanion.shared.mail.MailService;
import com.learningcompanion.shared.mail.MailTemplate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Thư cảnh báo ngắt mạch cho quản trị viên; chỉ gửi sau khi cảnh báo đã ghi xong. */
@Component
public class AiGatewayAlertMail {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final MailService mailService;

    public AiGatewayAlertMail(MailService mailService) {
        this.mailService = mailService;
    }

    @TransactionalEventListener
    public void on(AiGatewayAlertService.CircuitOpenedNotice notice) {
        mailService.send(notice.email(), MailTemplate.AI_CIRCUIT_OPEN, Map.of(
                "displayName", notice.displayName(),
                "capability", capabilityLabel(notice.capability()),
                "provider", notice.providerCode(),
                "fallback", notice.fallbackCode() == null ? "" : notice.fallbackCode(),
                "openedAt", TIME.format(notice.openedAt())));
    }

    static String capabilityLabel(AiCapability capability) {
        return switch (capability) {
            case TEXT_GENERATION -> "sinh văn bản";
            case EMBEDDING -> "biểu diễn ngữ nghĩa";
            case VISION -> "đọc hình ảnh";
            case OCR -> "nhận dạng chữ";
        };
    }
}
