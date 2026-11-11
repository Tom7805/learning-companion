package com.learningcompanion.workspace.application;

import com.learningcompanion.library.application.DocumentUsageProbe;
import com.learningcompanion.workspace.domain.WorkspaceDocumentRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Trả lời cho kho tài liệu: những không gian học nào đang dùng các tài liệu sắp xóa (NCL-06-CN-001 TC-03). */
@Component
public class WorkspaceUsageProbe implements DocumentUsageProbe {

    private final WorkspaceDocumentRepository links;

    public WorkspaceUsageProbe(WorkspaceDocumentRepository links) {
        this.links = links;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsedBy> usedBy(UUID ownerId, Collection<UUID> documentIds) {
        return WorkspaceDocumentLinkService.usedBy(links, ownerId, documentIds).stream()
                .map(workspace -> new UsedBy(workspace.getId(), workspace.getName()))
                .toList();
    }
}
