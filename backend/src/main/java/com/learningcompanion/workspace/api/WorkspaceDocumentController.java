package com.learningcompanion.workspace.api;

import com.learningcompanion.library.api.dto.DocumentCardResponse;
import com.learningcompanion.shared.ownership.OwnerContext;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import com.learningcompanion.workspace.api.dto.AttachDocumentsRequest;
import com.learningcompanion.workspace.api.dto.WorkspaceResponse;
import com.learningcompanion.workspace.application.WorkspaceDocumentLinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tài liệu của kho gắn với không gian học (NCL-06-CN-001, QTN-17): liệt kê, gắn, gỡ, và tra những không gian học đang
 * dùng một tài liệu.
 */
@RestController
@RequestMapping(ApiPaths.WORKSPACES)
public class WorkspaceDocumentController {

    private final WorkspaceDocumentLinkService links;
    private final OwnerContext owner;

    public WorkspaceDocumentController(WorkspaceDocumentLinkService links, OwnerContext owner) {
        this.links = links;
        this.owner = owner;
    }

    @GetMapping("/{id}/documents")
    public List<AttachedDocumentResponse> documents(@PathVariable UUID id, HttpServletRequest request) {
        return links.documents(id, ClientInfo.from(request)).stream()
                .map(attached -> new AttachedDocumentResponse(DocumentCardResponse.of(attached.document()),
                        attached.attachedAt()))
                .toList();
    }

    /** Gắn tài liệu từ kho; tài liệu đã gắn sẵn thì bỏ qua. Không tải lại, không xử lý lại (TC-02). */
    @PostMapping("/{id}/documents")
    public WorkspaceDocumentLinkService.AttachResult attach(@PathVariable UUID id,
                                                            @Valid @RequestBody AttachDocumentsRequest body,
                                                            HttpServletRequest request) {
        return links.attach(id, body.documentIds(), ClientInfo.from(request));
    }

    /** Gỡ tài liệu khỏi không gian học: chỉ bỏ liên kết, tài liệu vẫn ở kho (TC-04). */
    @DeleteMapping("/{id}/documents/{documentId}")
    public ResponseEntity<Void> detach(@PathVariable UUID id, @PathVariable UUID documentId,
                                       HttpServletRequest request) {
        links.detach(id, documentId, ClientInfo.from(request));
        return ResponseEntity.noContent().build();
    }

    /**
     * Các không gian học đang dùng những tài liệu này, hoặc tài liệu nằm trong thư mục này. Giao diện gọi cho khung
     * thông tin tài liệu và trước khi xóa, để báo tên các không gian học bị ảnh hưởng (TC-03).
     */
    @GetMapping("/usage")
    public List<WorkspaceResponse.Summary> usage(@RequestParam(name = "documentId", required = false)
                                                 List<UUID> documentIds,
                                                 @RequestParam(required = false) UUID folderId,
                                                 HttpServletRequest request) {
        return links.usage(owner.requireOwnerId(), documentIds, folderId, ClientInfo.from(request)).stream()
                .map(WorkspaceResponse.Summary::of)
                .toList();
    }

    public record AttachedDocumentResponse(DocumentCardResponse document, Instant attachedAt) {
    }
}
