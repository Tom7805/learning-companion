package com.learningcompanion.shared.ownership;

import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.web.ClientInfo;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi lần bị từ chối vào nhật ký trong giao dịch riêng, nên vẫn được lưu dù thao tác đọc bị hủy
 * hoặc đang chạy chỉ đọc. Chỉ ghi mã đối tượng, không bao giờ ghi nội dung (QTN-06).
 */
@Component
public class AccessDeniedLogger {

    private final AuditLogger audit;

    public AccessDeniedLogger(AuditLogger audit) {
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notOwner(UUID requesterId, AuditObjectType objectType, UUID objectId, ClientInfo client) {
        audit.byUser(requesterId, AuditAction.LEARNING_ACCESS_DENIED, objectType, objectId,
                Map.of("reason", "NOT_OWNER"), client);
    }

    /**
     * Người đã đăng nhập gọi một điểm cuối không thuộc vai trò của mình, ví dụ người học mở khu quản trị bằng đường
     * dẫn trực tiếp (NCL-03-CN-001 TC-05). Chỉ ghi phương thức và đường dẫn.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void wrongRole(UUID requesterId, String method, String path, ClientInfo client) {
        audit.byUser(requesterId, AuditAction.ROLE_ACCESS_DENIED, AuditObjectType.ENDPOINT, null,
                Map.of("method", method, "path", path), client);
    }
}
