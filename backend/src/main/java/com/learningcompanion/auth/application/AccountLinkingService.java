package com.learningcompanion.auth.application;

import com.learningcompanion.auth.domain.ExternalIdentity;
import com.learningcompanion.auth.domain.ExternalIdentityRepository;
import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.ownership.OwnershipGuard;
import com.learningcompanion.shared.web.ClientInfo;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tài khoản bên ngoài đã liên kết, xem và gỡ trong phần cài đặt (NCL-01-CN-007). Chỉ gỡ được khi tài khoản
 * đã có mật khẩu, nếu không người học sẽ không còn cách nào đăng nhập (TC-04).
 */
@Service
public class AccountLinkingService {

    private final ExternalIdentityRepository identities;
    private final UserAccountRepository accounts;
    private final OwnershipGuard guard;
    private final AuditLogger audit;

    public AccountLinkingService(ExternalIdentityRepository identities, UserAccountRepository accounts,
                                 OwnershipGuard guard, AuditLogger audit) {
        this.identities = identities;
        this.accounts = accounts;
        this.guard = guard;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ExternalIdentity> list(UUID accountId, ClientInfo client) {
        guard.requireSelf(accountId, accounts::existsById, AuditObjectType.USER_ACCOUNT, client);
        return identities.findByUserIdOrderByLinkedAtAsc(accountId);
    }

    @Transactional
    public void unlink(UUID accountId, UUID identityId, ClientInfo client) {
        guard.requireSelf(accountId, accounts::existsById, AuditObjectType.USER_ACCOUNT, client);
        ExternalIdentity identity = identities.findById(identityId)
                .filter(found -> found.getUserId().equals(accountId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        UserAccount account = accounts.findById(accountId).orElseThrow();
        if (!account.hasPassword()) {
            throw new BusinessException(ErrorCode.PASSWORD_REQUIRED_TO_UNLINK);
        }
        identities.delete(identity);
        audit.byUser(accountId, AuditAction.EXTERNAL_IDENTITY_UNLINKED, AuditObjectType.EXTERNAL_IDENTITY,
                identity.getId(), Map.of("provider", identity.getProvider().name()), client);
    }
}
