package com.learningcompanion.shared.mail;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Bản mô phỏng: giữ thư trong bộ nhớ để xem ở hộp thư thử và cho kiểm thử tự động đọc lại. */
@Component
@ConditionalOnProperty(name = "app.mail.gateway", havingValue = "mock")
public class MockMailGateway implements MailGateway {

    private static final Logger log = LoggerFactory.getLogger(MockMailGateway.class);
    private static final int CAPACITY = 200;
    private static final Pattern LINK = Pattern.compile("href=\"(https?://[^\"]+)\"");

    private final Deque<CapturedMail> mailbox = new ConcurrentLinkedDeque<>();
    private final Clock clock;

    public MockMailGateway(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void send(OutgoingMail mail) {
        List<String> links = new ArrayList<>();
        Matcher matcher = LINK.matcher(mail.html());
        while (matcher.find()) {
            links.add(matcher.group(1).replace("&amp;", "&"));
        }
        mailbox.addFirst(new CapturedMail(mail.to(), mail.subject(), mail.template().name(), mail.html(),
                List.copyOf(links), Instant.now(clock)));
        while (mailbox.size() > CAPACITY) {
            mailbox.pollLast();
        }
        log.info("[mock-mail] {} -> {}", mail.template(), mail.to());
    }

    public List<CapturedMail> messages() {
        return List.copyOf(mailbox);
    }

    public void clear() {
        mailbox.clear();
    }

    public record CapturedMail(String to, String subject, String template, String html, List<String> links,
                               Instant sentAt) {
    }
}
