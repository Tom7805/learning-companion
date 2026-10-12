package com.learningcompanion.shared.ownership;

import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.web.ClientInfo;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * Lấy một bản ghi học tập của người đang đăng nhập. Bản ghi của người khác được trả lời y hệt
 * bản ghi không tồn tại (404), để không ai dò được mã nào đang có dữ liệu.
 */
@Component
public class OwnershipGuard {

    private final OwnerContext owner;
    private final AccessDeniedLogger deniedLogger;

    public OwnershipGuard(OwnerContext owner, AccessDeniedLogger deniedLogger) {
        this.owner = owner;
        this.deniedLogger = deniedLogger;
    }

    public <T extends OwnedEntity> T require(OwnerScopedRepository<T> repository, UUID id,
                                             AuditObjectType objectType, ClientInfo client) {
        UUID ownerId = owner.requireOwnerId();
        return repository.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> {
            if (repository.existsById(id)) {
                deniedLogger.notOwner(ownerId, objectType, id, client);
            }
            return new BusinessException(ErrorCode.NOT_FOUND);
        });
    }

    /**
     * Cho thao tác trên dữ liệu gắn trực tiếp với tài khoản (ví dụ đồng ý theo mục đích): chỉ chính chủ tài khoản
     * mới được làm. Mã tài khoản của người khác được trả lời như không tồn tại, lần từ chối được ghi nhật ký.
     */
    public UUID requireSelf(UUID accountId, Predicate<UUID> accountExists, AuditObjectType objectType,
                            ClientInfo client) {
        UUID ownerId = owner.requireOwnerId();
        if (!ownerId.equals(accountId)) {
            if (accountExists.test(accountId)) {
                deniedLogger.notOwner(ownerId, objectType, accountId, client);
            }
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return ownerId;
    }
}
