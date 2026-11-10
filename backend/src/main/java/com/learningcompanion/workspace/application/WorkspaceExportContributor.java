package com.learningcompanion.workspace.application;

import com.learningcompanion.library.domain.Document;
import com.learningcompanion.library.domain.DocumentRepository;
import com.learningcompanion.shared.export.DataExportContributor;
import com.learningcompanion.shared.export.ExportFormat;
import com.learningcompanion.shared.export.ExportWriter;
import com.learningcompanion.workspace.domain.Workspace;
import com.learningcompanion.workspace.domain.WorkspaceDocument;
import com.learningcompanion.workspace.domain.WorkspaceDocumentRepository;
import com.learningcompanion.workspace.domain.WorkspaceRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phần "Không gian học" của gói xuất (NCL-06-CN-001): bảng kê các không gian học và tài liệu đang gắn với từng không
 * gian. Tệp của tài liệu nằm ở phần thư viện, ở đây chỉ ghi mã và tên để đối chiếu (QTN-17).
 */
@Component
public class WorkspaceExportContributor implements DataExportContributor {

    private final WorkspaceRepository workspaces;
    private final WorkspaceDocumentRepository links;
    private final DocumentRepository documents;

    public WorkspaceExportContributor(WorkspaceRepository workspaces, WorkspaceDocumentRepository links,
                                      DocumentRepository documents) {
        this.workspaces = workspaces;
        this.links = links;
        this.documents = documents;
    }

    @Override
    public String section() {
        return "workspaces";
    }

    @Override
    public String title() {
        return "Không gian học";
    }

    @Override
    public List<String> describe() {
        return List.of("workspaces.json: bảng kê không gian học (tên, biểu tượng, màu, mô tả, thời điểm) và các tài liệu "
                + "của thư viện đang gắn với từng không gian.");
    }

    @Override
    public List<ExportFormat> formats() {
        return List.of(ExportFormat.JSON);
    }

    @Override
    public int order() {
        return 45;
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(UUID ownerId, ExportWriter writer) {
        List<Workspace> mine = workspaces.findAllByOwnerIdOrderByCreatedAtAsc(ownerId);
        if (mine.isEmpty()) {
            return;
        }
        Map<UUID, Document> documentById = new HashMap<>();
        documents.findAllByOwnerIdOrderByCreatedAtDesc(ownerId)
                .forEach(document -> documentById.put(document.getId(), document));
        Map<UUID, List<Map<String, Object>>> attached = new HashMap<>();
        for (WorkspaceDocument link : links.findAllByOwnerIdOrderByAttachedAtAsc(ownerId)) {
            Document document = documentById.get(link.getDocumentId());
            if (document == null) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", document.getId());
            entry.put("title", document.getTitle());
            entry.put("attachedAt", link.getAttachedAt());
            entry.put("inTrash", document.isTrashed());
            attached.computeIfAbsent(link.getWorkspaceId(), key -> new ArrayList<>()).add(entry);
        }
        List<Map<String, Object>> index = new ArrayList<>();
        for (Workspace workspace : mine) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", workspace.getId());
            entry.put("name", workspace.getName());
            entry.put("icon", workspace.getIcon().name());
            entry.put("color", workspace.getColor().name());
            entry.put("description", workspace.getDescription());
            entry.put("createdAt", workspace.getCreatedAt());
            entry.put("updatedAt", workspace.getUpdatedAt());
            entry.put("inTrash", workspace.isTrashed());
            entry.put("documents", attached.getOrDefault(workspace.getId(), List.of()));
            index.add(entry);
        }
        writer.json("workspaces.json", index);
    }
}
