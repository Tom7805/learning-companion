package com.learningcompanion.library.application;

import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.ownership.OwnerContext;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * Chốt chặn trước khi đưa tài liệu vào thùng rác (NCL-06-CN-001 TC-03): tài liệu đang gắn với không gian học thì yêu
 * cầu xóa bị từ chối kèm tên các không gian học bị ảnh hưởng, cho tới khi người học xác nhận đã biết.
 */
@Service
public class DocumentUsageGuard {

    private final DocumentUsageProbe probe;
    private final OwnerContext owner;

    public DocumentUsageGuard(DocumentUsageProbe probe, OwnerContext owner) {
        this.probe = probe;
        this.owner = owner;
    }

    @Transactional(readOnly = true)
    public void requireConfirmed(Collection<UUID> documentIds, boolean confirmed) {
        if (confirmed || documentIds == null || documentIds.isEmpty()) {
            return;
        }
        List<DocumentUsageProbe.UsedBy> used = probe.usedBy(owner.requireOwnerId(), documentIds);
        if (!used.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("workspaces", used);
            throw new BusinessException(ErrorCode.LIBRARY_DOCUMENT_IN_USE, null, details);
        }
    }
}
