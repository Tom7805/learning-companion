package com.learningcompanion.library.application;

import com.learningcompanion.library.domain.UploadSessionRepository;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.storage.ChunkedUploadStore;
import com.learningcompanion.shared.web.ClientInfo;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bỏ một lần tải lên và ghi nhật ký lần từ chối trong giao dịch riêng, nên vẫn được lưu dù yêu cầu đang xử lý kết thúc
 * bằng lỗi (giao dịch của yêu cầu bị hủy). Nhật ký chỉ ghi lý do và cỡ tệp, không ghi tên hay nội dung tệp (QTN-06).
 */
@Component
public class UploadRejections {

    private final UploadSessionRepository sessions;
    private final ChunkedUploadStore parts;
    private final AuditLogger audit;

    public UploadRejections(UploadSessionRepository sessions, ChunkedUploadStore parts, AuditLogger audit) {
        this.sessions = sessions;
        this.parts = parts;
        this.audit = audit;
    }

    /** Xóa phiên và mọi phần đã nhận; tệp không được lưu lại ở đâu cả. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void discard(UUID ownerId, UUID uploadId) {
        sessions.findByIdAndOwnerId(uploadId, ownerId).ifPresent(sessions::delete);
        parts.discard(ownerId, uploadId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reject(UUID ownerId, UUID uploadId, String reason, long sizeBytes, ClientInfo client) {
        if (uploadId != null) {
            sessions.findByIdAndOwnerId(uploadId, ownerId).ifPresent(sessions::delete);
            parts.discard(ownerId, uploadId);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason);
        details.put("sizeBytes", sizeBytes);
        audit.byUser(ownerId, AuditAction.DOCUMENT_UPLOAD_REJECTED, AuditObjectType.LIBRARY_UPLOAD, uploadId, details,
                client);
    }
}
