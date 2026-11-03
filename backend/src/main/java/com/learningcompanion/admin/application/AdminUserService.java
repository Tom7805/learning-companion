package com.learningcompanion.admin.application;

import com.learningcompanion.admin.api.dto.AdminIdentity;
import com.learningcompanion.admin.api.dto.AdminUserSummary;
import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.auth.domain.UserSession;
import com.learningcompanion.auth.domain.UserSessionRepository;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.ownership.LearningDataUsage;
import com.learningcompanion.shared.web.ClientInfo;
import java.time.Instant;
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
    private final UserSessionRepository sessions;
    private final RecoveryCodeService recoveryCodes;

    public AdminUserService(UserAccountRepository accounts, List<LearningDataUsage> usageSources, AuditLogger audit,
                            UserSessionRepository sessions, RecoveryCodeService recoveryCodes) {
        this.accounts = accounts;
        this.usageSources = usageSources;
        this.audit = audit;
        this.sessions = sessions;
        this.recoveryCodes = recoveryCodes;
    }

    @Transactional(readOnly = true)
    public AdminIdentity identity(UUID adminId, UUID sessionId) {
        UserAccount admin = accounts.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Instant sessionExpiresAt = sessions.findById(sessionId).map(UserSession::getMaxExpiresAt).orElse(null);
        return new AdminIdentity(admin.getId(), admin.getEmail(), admin.getDisplayName(), sessionExpiresAt,
                recoveryCodes.remaining(adminId));
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
