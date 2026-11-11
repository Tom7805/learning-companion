package com.learningcompanion.library.api;

import com.learningcompanion.library.api.dto.FolderRequest;
import com.learningcompanion.library.api.dto.FolderTreeResponse;
import com.learningcompanion.library.api.dto.FolderTreeResponse.FolderResponse;
import com.learningcompanion.library.application.DocumentUsageGuard;
import com.learningcompanion.library.application.FolderService;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Cây thư mục của kho tài liệu (NCL-05-CN-001); thư mục của người khác luôn được trả lời 404. */
@RestController
@RequestMapping(ApiPaths.LIBRARY + "/folders")
public class FolderController {

    private final FolderService folders;
    private final DocumentUsageGuard usage;

    public FolderController(FolderService folders, DocumentUsageGuard usage) {
        this.folders = folders;
        this.usage = usage;
    }

    @GetMapping
    public FolderTreeResponse tree() {
        return FolderTreeResponse.of(folders.tree());
    }

    /** Báo tên trùng trước khi lưu: giao diện hỏi khi người học đang gõ tên thư mục. */
    @GetMapping("/name-check")
    public FolderService.NameCheck checkName(@RequestParam(required = false) UUID parentId,
                                             @RequestParam String name,
                                             @RequestParam(required = false) UUID exceptId,
                                             HttpServletRequest request) {
        return folders.checkName(parentId, name, exceptId, ClientInfo.from(request));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FolderResponse create(@Valid @RequestBody FolderRequest body, HttpServletRequest request) {
        return FolderResponse.of(folders.create(body.parentId(), body.name(), ClientInfo.from(request)), 0);
    }

    @PatchMapping("/{id}")
    public FolderResponse rename(@PathVariable UUID id, @Valid @RequestBody RenameRequest body,
                                 HttpServletRequest request) {
        return FolderResponse.of(folders.rename(id, body.name(), ClientInfo.from(request)), 0);
    }

    @PostMapping("/{id}/move")
    public FolderResponse move(@PathVariable UUID id, @RequestBody MoveRequest body, HttpServletRequest request) {
        return FolderResponse.of(folders.move(id, body.parentId(), ClientInfo.from(request)), 0);
    }

    /**
     * Thư mục cùng mọi thứ bên trong vào thùng rác (QTN-18). Bên trong có tài liệu đang gắn với không gian học thì chỉ
     * xóa khi có {@code confirmInUse=true} (NCL-06-CN-001 TC-03).
     */
    @DeleteMapping("/{id}")
    public FolderService.Trashed trash(@PathVariable UUID id,
                                       @RequestParam(defaultValue = "false") boolean confirmInUse,
                                       HttpServletRequest request) {
        ClientInfo client = ClientInfo.from(request);
        usage.requireConfirmed(folders.activeDocumentIdsUnder(id, client), confirmInUse);
        return folders.trash(id, client);
    }

    public record RenameRequest(@NotBlank @Size(max = 120) String name) {
    }

    public record MoveRequest(UUID parentId) {
    }
}
