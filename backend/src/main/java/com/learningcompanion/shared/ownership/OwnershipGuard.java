package com.learningcompanion.shared.ownership;

import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.web.ClientInfo;
import java.util.UUID;
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
}
