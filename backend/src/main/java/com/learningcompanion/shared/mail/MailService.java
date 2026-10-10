package com.learningcompanion.shared.mail;

import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Dựng thư từ mẫu và gửi ở luồng nền, nên thời gian phản hồi của yêu cầu không phụ thuộc
 * vào việc có gửi thư hay không. Lỗi tạm thời được thử lại vài lần.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 2_000;
    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi");

    private final MailGateway gateway;
    private final ITemplateEngine templateEngine;

    public MailService(MailGateway gateway, ITemplateEngine templateEngine) {
        this.gateway = gateway;
        this.templateEngine = templateEngine;
    }

    @Async
    public void send(String to, MailTemplate template, Map<String, Object> variables) {
        Context context = new Context(VIETNAMESE, variables);
        context.setVariable("subject", template.subject());
        String html = templateEngine.process(template.view(), context);
        MailGateway.OutgoingMail mail = new MailGateway.OutgoingMail(to, template.subject(), html, template);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                gateway.send(mail);
                return;
            } catch (RuntimeException e) {
                log.warn("Sending {} failed (attempt {}/{})", template, attempt, MAX_ATTEMPTS, e);
                if (attempt < MAX_ATTEMPTS) {
                    sleep();
                }
            }
        }
        log.error("Giving up sending {} after {} attempts", template, MAX_ATTEMPTS);
    }

    private static void sleep() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
