package com.learningcompanion.workspace.application;

import com.learningcompanion.library.application.DocumentService;
import com.learningcompanion.library.application.FolderService;
import com.learningcompanion.library.domain.Document;
import com.learningcompanion.shared.web.ClientInfo;
import com.learningcompanion.workspace.domain.Workspace;
import com.learningcompanion.workspace.domain.WorkspaceDocument;
import com.learningcompanion.workspace.domain.WorkspaceDocumentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gắn và gỡ tài liệu của kho với không gian học (NCL-06-CN-001, QTN-17). Gắn chỉ thêm liên kết: tài liệu không được
 * sao chép hay xử lý lại, dung lượng đã dùng không đổi. Gỡ chỉ bỏ liên kết: tài liệu vẫn nguyên trong kho.
 */
@Service
public class WorkspaceDocumentLinkService {

    private final WorkspaceService workspaces;
    private final WorkspaceDocumentRepository links;
    private final DocumentService documents;
    private final FolderService folders;
    private final Clock clock;

    public WorkspaceDocumentLinkService(WorkspaceService workspaces, WorkspaceDocumentRepository links,
                                        DocumentService documents, FolderService folders, Clock clock) {
        this.workspaces = workspaces;
        this.links = links;
        this.documents = documents;
        this.folders = folders;
        this.clock = clock;
    }

    /** Tài liệu còn trong kho đang gắn với không gian học, mới gắn đứng trước. */
    @Transactional(readOnly = true)
    public List<Attached> documents(UUID workspaceId, ClientInfo client) {
        Workspace workspace = workspaces.require(workspaceId, client);
        return links.findActiveDocumentsByOwnerIdAndWorkspaceId(workspace.getOwnerId(), workspace.getId()).stream()
                .map(row -> new Attached((Document) row[0], (Instant) row[1]))
                .toList();
    }

    /**
     * Gắn các tài liệu của kho vào không gian học. Tài liệu đã gắn sẵn thì bỏ qua; chỉ cần một tài liệu không thuộc
     * người học là cả lần gắn bị từ chối, không đổi gì.
     */
    @Transactional
    public AttachResult attach(UUID workspaceId, Collection<UUID> documentIds, ClientInfo client) {
        Workspace workspace = workspaces.require(workspaceId, client);
        List<Document> chosen = documents.requireAll(documentIds, client);
        Set<UUID> already = new LinkedHashSet<>();
        links.findAllByOwnerIdAndWorkspaceId(workspace.getOwnerId(), workspace.getId())
                .forEach(link -> already.add(link.getDocumentId()));
        Instant now = clock.instant();
        int attached = 0;
        for (Document document : chosen) {
            if (already.add(document.getId())) {
                links.save(WorkspaceDocument.link(workspace.getOwnerId(), workspace.getId(), document.getId(), now));
                attached++;
            }
        }
        if (attached > 0) {
            workspace.touch(now);
        }
        return new AttachResult(attached, chosen.size() - attached);
    }

    /** Gỡ tài liệu khỏi không gian học: chỉ bỏ liên kết (TC-04). Tài liệu chưa gắn thì không có gì để gỡ. */
    @Transactional
    public boolean detach(UUID workspaceId, UUID documentId, ClientInfo client) {
        Workspace workspace = workspaces.require(workspaceId, client);
        var link = links.findByOwnerIdAndWorkspaceIdAndDocumentId(workspace.getOwnerId(), workspace.getId(),
                documentId);
        if (link.isEmpty()) {
            return false;
        }
        links.delete(link.get());
        workspace.touch(clock.instant());
        return true;
    }

    /**
     * Các không gian học đang dùng những tài liệu này hoặc tài liệu trong thư mục này (kể cả thư mục con cháu), mỗi không
     * gian học một lần: cho khung thông tin tài liệu và lời báo trước khi xóa (TC-03).
     */
    @Transactional(readOnly = true)
    public List<Workspace> usage(UUID ownerId, Collection<UUID> documentIds, UUID folderId, ClientInfo client) {
        List<UUID> ids = new ArrayList<>(documentIds == null ? List.of() : documentIds);
        if (folderId != null) {
            ids.addAll(folders.activeDocumentIdsUnder(folderId, client));
        }
        return usedBy(links, ownerId, ids);
    }

    static List<Workspace> usedBy(WorkspaceDocumentRepository links, UUID ownerId, Collection<UUID> documentIds) {
        if (documentIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, Workspace> found = new LinkedHashMap<>();
        for (Object[] row : links.findActiveWorkspacesByOwnerIdAndDocumentIdIn(ownerId, documentIds)) {
            Workspace workspace = (Workspace) row[1];
            found.putIfAbsent(workspace.getId(), workspace);
        }
        return List.copyOf(found.values());
    }

    public record Attached(Document document, Instant attachedAt) {
    }

    /** {@code attached}: số liên kết mới; {@code alreadyAttached}: số tài liệu đã gắn từ trước. */
    public record AttachResult(int attached, int alreadyAttached) {
    }
}
