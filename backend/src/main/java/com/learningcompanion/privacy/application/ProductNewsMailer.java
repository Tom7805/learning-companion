package com.learningcompanion.privacy.application;

import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.privacy.domain.Consent;
import com.learningcompanion.privacy.domain.ConsentPurpose;
import com.learningcompanion.privacy.domain.ConsentRepository;
import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.mail.MailService;
import com.learningcompanion.shared.mail.MailTemplate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thư giới thiệu tính năng mới (TC-02): danh sách người nhận được lấy ngay lúc gửi từ những người đang
 * đồng ý, nên ai vừa tắt mục này sẽ không nhận thư kế tiếp. Mỗi thư kèm đường dẫn tắt nhận thư.
 */
@Service
public class ProductNewsMailer {

    private final ConsentRepository consents;
    private final UserAccountRepository accounts;
    private final MailService mail;
    private final AppProperties properties;

    public ProductNewsMailer(ConsentRepository consents, UserAccountRepository accounts, MailService mail,
                             AppProperties properties) {
        this.consents = consents;
        this.accounts = accounts;
        this.mail = mail;
        this.properties = properties;
    }

    /** Gửi một bản tin; trả về số người nhận. */
    @Transactional(readOnly = true)
    public int send(String headline, String body) {
        // Tài khoản đang chờ xóa không nhận bản tin (NCL-01-CN-009).
        List<UserAccount> recipients = accounts.findAllById(consents.findByPurposeAndGrantedTrue(
                ConsentPurpose.PRODUCT_NEWS_EMAIL).stream().map(Consent::getUserId).toList()).stream()
                .filter(account -> !account.isPendingDeletion())
                .toList();
        String settingsUrl = properties.frontendUrl() + "/privacy";
        recipients.forEach(account -> mail.send(account.getEmail(), MailTemplate.PRODUCT_NEWS, Map.of(
                "displayName", account.getDisplayName(),
                "headline", headline,
                "body", body,
                "settingsUrl", settingsUrl)));
        return recipients.size();
    }
}
