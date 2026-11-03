package com.learningcompanion.shared.audit;

import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.web.ClientInfo;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nhật ký chỉ được ghi thêm (QTN-06, NCL-03-CN-001 TC-03): mọi yêu cầu sửa hay xóa một dòng đều bị từ chối, và lần cố
 * can thiệp được ghi lại trong giao dịch riêng để vẫn còn dù yêu cầu bị hủy. Cơ sở dữ liệu cũng chặn sửa xóa bằng
 * trigger, nên kể cả mã lỗi ở tầng ứng dụng cũng không làm đổi được nhật ký.
 */
@Component
public class AuditTamperGuard {

    private final AuditLogger audit;

    public AuditTamperGuard(AuditLogger audit) {
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAttempt(UUID requesterId, UUID auditLogId, String method, ClientInfo client) {
        audit.byUser(requesterId, AuditAction.AUDIT_LOG_TAMPER_ATTEMPTED, AuditObjectType.AUDIT_LOG, auditLogId,
                Map.of("method", method), client);
    }

    public BusinessException rejection() {
        return new BusinessException(ErrorCode.AUDIT_LOG_IMMUTABLE);
    }
}
