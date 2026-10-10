package com.learningcompanion.shared.mail;

import com.learningcompanion.shared.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mail.gateway", havingValue = "smtp")
public class SmtpMailGateway implements MailGateway {

    private final JavaMailSender sender;
    private final AppProperties properties;

    public SmtpMailGateway(JavaMailSender sender, AppProperties properties) {
        this.sender = sender;
        this.properties = properties;
    }

    @Override
    public void send(OutgoingMail mail) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.mail().fromAddress(), properties.mail().fromName());
            helper.setTo(mail.to());
            helper.setSubject(mail.subject());
            helper.setText(mail.html(), true);
            sender.send(message);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new MailSendException("Could not build message for " + mail.template(), e);
        }
    }
}
