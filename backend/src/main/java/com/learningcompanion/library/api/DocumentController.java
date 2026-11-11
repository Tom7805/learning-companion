package com.learningcompanion.library.api;

import com.learningcompanion.library.api.dto.BulkActionRequest;
import com.learningcompanion.library.api.dto.DocumentCardResponse;
import com.learningcompanion.library.api.dto.DocumentChunkResponse;
import com.learningcompanion.library.api.dto.DocumentResponse;
import com.learningcompanion.library.application.BulkActionService;
import com.learningcompanion.library.application.DocumentQuery;
import com.learningcompanion.library.application.DocumentService;
import com.learningcompanion.library.application.DocumentUsageGuard;
import com.learningcompanion.library.domain.DocumentStatus;
import com.learningcompanion.library.domain.DocumentType;
import com.learningcompanion.shared.security.SignedUrlService;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tài liệu của người học đang đăng nhập; tài liệu của người khác hay trong thùng rác luôn được trả lời 404. */
@RestController
@RequestMapping(ApiPaths.LIBRARY + "/documents")
public class DocumentController {

    private final DocumentService documents;
    private final BulkActionService bulk;
    private final DocumentUsageGuard usage;

    public DocumentController(DocumentService documents, BulkActionService bulk, DocumentUsageGuard usage) {
        this.documents = documents;
        this.bulk = bulk;
        this.usage = usage;
    }

    /**
     * Danh sách theo phạm vi và bộ lọc (NCL-05-CN-001). Mặc định là mọi tài liệu, mới thêm trước. Tham số lặp lại được
     * cho nhiều giá trị, ví dụ {@code ?type=PPTX&tag=…&tag=…}.
     */
    @GetMapping
    public List<DocumentCardResponse> list(@RequestParam(required = false) DocumentQuery.Scope scope,
                                           @RequestParam(required = false) UUID folderId,
                                           @RequestParam(name = "type", required = false) Set<DocumentType> types,
                                           @RequestParam(name = "tag", required = false) Set<UUID> tagIds,
                                           @RequestParam(name = "status", required = false)
                                           Set<DocumentStatus> statuses,
                                           @RequestParam(required = false) Instant from,
                                           @RequestParam(required = false) Instant to,
                                           @RequestParam(required = false) DocumentQuery.Sort sort,
                                           @RequestParam(required = false) DocumentQuery.Direction direction,
                                           HttpServletRequest request) {
        DocumentQuery query = new DocumentQuery(scope, folderId, types, tagIds, statuses, from, to, sort, direction);
        return documents.list(query, ClientInfo.from(request)).stream().map(DocumentCardResponse::of).toList();
    }

    /**
     * Mở tài liệu: kèm nội dung, và ghi lại lần mở gần nhất. {@code track=false} khi chỉ xem khung thông tin, để lối tắt
     * "Gần đây" chỉ gồm tài liệu thật sự được mở.
     */
    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable UUID id, @RequestParam(defaultValue = "true") boolean track,
                                HttpServletRequest request) {
        ClientInfo client = ClientInfo.from(request);
        return DocumentResponse.of(track ? documents.open(id, client) : documents.get(id, client));
    }

    @PatchMapping("/{id}")
    public DocumentCardResponse rename(@PathVariable UUID id, @Valid @RequestBody RenameRequest body,
                                       HttpServletRequest request) {
        return DocumentCardResponse.of(documents.rename(id, body.title(), ClientInfo.from(request)));
    }

    @PutMapping("/{id}/star")
    public DocumentCardResponse star(@PathVariable UUID id, @Valid @RequestBody StarRequest body,
                                     HttpServletRequest request) {
        return DocumentCardResponse.of(documents.star(id, body.starred(), ClientInfo.from(request)));
    }

    @PutMapping("/{id}/tags")
    public DocumentCardResponse tags(@PathVariable UUID id, @Valid @RequestBody TagsRequest body,
                                     HttpServletRequest request) {
        return DocumentCardResponse.of(documents.setTags(id, body.tagIds(), ClientInfo.from(request)));
    }

    /**
     * Đưa vào thùng rác (QTN-18); tệp gốc vẫn giữ để khôi phục được. Tài liệu đang gắn với không gian học chỉ xóa khi
     * có {@code confirmInUse=true} (NCL-06-CN-001 TC-03).
     */
    @DeleteMapping("/{id}")
    public TrashedResponse trash(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean confirmInUse,
                                 HttpServletRequest request) {
        usage.requireConfirmed(List.of(id), confirmInUse);
        return new TrashedResponse(documents.trash(id, ClientInfo.from(request)).getTrashBatchId());
    }

    @PostMapping("/bulk")
    public BulkActionService.Result bulk(@Valid @RequestBody BulkActionRequest body, HttpServletRequest request) {
        if (body.action() == BulkActionService.Action.TRASH) {
            usage.requireConfirmed(body.documentIds(), Boolean.TRUE.equals(body.confirmInUse()));
        }
        return bulk.apply(body.action(), body.documentIds(), body.folderId(), body.tagIds(),
                ClientInfo.from(request));
    }

    /**
     * Các đoạn đã lập chỉ mục, theo thứ tự, kèm vị trí trong tài liệu gốc (NCL-05-CN-003). Không kèm vector. Tài liệu
     * của người khác trả 404 và lần từ chối được ghi nhật ký (TC-05).
     */
    @GetMapping("/{id}/chunks")
    public List<DocumentChunkResponse> chunks(@PathVariable UUID id, HttpServletRequest request) {
        return documents.chunks(id, ClientInfo.from(request)).stream().map(DocumentChunkResponse::of).toList();
    }

    /** Thử xử lý lại tài liệu lỗi (NCL-05-CN-003 TC-03): tài liệu quay về hàng chờ xử lý. */
    @PostMapping("/{id}/reprocess")
    public DocumentCardResponse reprocess(@PathVariable UUID id, HttpServletRequest request) {
        return DocumentCardResponse.of(documents.reprocess(id, ClientInfo.from(request)));
    }

    /** Xin đường dẫn mở tệp gốc mới; giao diện gọi lại khi đường dẫn cũ hết hạn. */
    @PostMapping("/{id}/file-link")
    public FileLinkResponse fileLink(@PathVariable UUID id, HttpServletRequest request) {
        SignedUrlService.SignedLink link = documents.fileLink(id, ClientInfo.from(request));
        return new FileLinkResponse(ApiPaths.FILES + "/" + link.token(), link.expiresAt());
    }

    public record FileLinkResponse(String url, Instant expiresAt) {
    }

    public record RenameRequest(@NotBlank @Size(max = DocumentService.TITLE_MAX_LENGTH) String title) {
    }

    public record StarRequest(@NotNull Boolean starred) {
    }

    public record TagsRequest(@NotNull List<UUID> tagIds) {
    }

    public record TrashedResponse(UUID trashBatchId) {
    }
}
