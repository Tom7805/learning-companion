package com.learningcompanion.admin.application;

import com.learningcompanion.admin.api.dto.AdminUserSummary;
import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.ownership.LearningDataUsage;
import com.learningcompanion.shared.web.ClientInfo;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Quản trị viên xem tài khoản người học: chỉ số liệu, không nội dung (NCL-01-CN-004, QTN-07). */
@Service
public class AdminUserService {

    private final UserAccountRepository accounts;
    private final List<LearningDataUsage> usageSources;
    private final AuditLogger audit;

    public AdminUserService(UserAccountRepository accounts, List<LearningDataUsage> usageSources, AuditLogger audit) {
        this.accounts = accounts;
        this.usageSources = usageSources;
        this.audit = audit;
    }

    @Transactional
    public AdminUserSummary summary(UUID adminId, UUID accountId, ClientInfo client) {
        UserAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        LearningDataUsage.Usage usage = usageSources.stream()
                .map(source -> source.usageOf(accountId))
                .reduce(new LearningDataUsage.Usage(0, 0), LearningDataUsage.Usage::plus);
        audit.byUser(adminId, AuditAction.ADMIN_ACCOUNT_VIEWED, AuditObjectType.USER_ACCOUNT, accountId,
                Map.of(), client);
        return new AdminUserSummary(account.getId(), account.getEmail(), account.getDisplayName(),
                account.getRole().name(), account.getStatus().name(), account.getCreatedAt(),
                account.getEmailVerifiedAt(), usage.records(), usage.bytes());
    }
}
