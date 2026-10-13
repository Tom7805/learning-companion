package com.learningcompanion.privacy.application;

import com.learningcompanion.auth.application.EmailNormalizer;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.shared.mail.MailRecipientPolicy;
import com.learningcompanion.shared.mail.MailTemplate;
import org.springframework.stereotype.Component;

/**
 * Tài khoản đang chờ xóa ngừng nhận mọi thông báo không liên quan tới yêu cầu xóa (NCL-01-CN-009): bản tin, cảnh báo
 * thiết bị mới, gói dữ liệu... Chỉ thư về yêu cầu xóa và thư đặt lại mật khẩu người học tự yêu cầu được gửi.
 */
@Component
public class PendingDeletionMailPolicy implements MailRecipientPolicy {

    private final UserAccountRepository accounts;

    public PendingDeletionMailPolicy(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean allows(String to, MailTemplate template) {
        if (template.deliveredWhilePendingDeletion()) {
            return true;
        }
        return accounts.findByEmail(EmailNormalizer.normalize(to))
                .map(account -> !account.isPendingDeletion())
                .orElse(true);
    }
}
