package com.learningcompanion.workspace.api;

import com.learningcompanion.library.api.dto.FolderTreeResponse.FolderResponse;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import com.learningcompanion.workspace.api.dto.WorkspaceRequest;
import com.learningcompanion.workspace.api.dto.WorkspaceResponse;
import com.learningcompanion.workspace.application.WorkspaceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Không gian học của người học đang đăng nhập (NCL-06-CN-001); không gian học của người khác hay trong thùng rác luôn
 * được trả lời 404.
 */
@RestController
@RequestMapping(ApiPaths.WORKSPACES)
public class WorkspaceController {

    private final WorkspaceService workspaces;

    public WorkspaceController(WorkspaceService workspaces) {
        this.workspaces = workspaces;
    }

    @GetMapping
    public List<WorkspaceResponse> list() {
        return workspaces.list().stream().map(WorkspaceResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceResponse create(@Valid @RequestBody WorkspaceRequest body) {
        return WorkspaceResponse.of(workspaces.create(body.name(), body.icon(), body.color(), body.description()));
    }

    /** Mở không gian học; lần mở được ghi lại để thanh bên đưa không gian học vừa dùng lên đầu. */
    @GetMapping("/{id}")
    public WorkspaceResponse get(@PathVariable UUID id, HttpServletRequest request) {
        return WorkspaceResponse.of(workspaces.open(id, ClientInfo.from(request)));
    }

    @PatchMapping("/{id}")
    public WorkspaceResponse update(@PathVariable UUID id, @Valid @RequestBody WorkspaceRequest body,
                                    HttpServletRequest request) {
        return WorkspaceResponse.of(workspaces.update(id, body.name(), body.icon(), body.color(), body.description(),
                ClientInfo.from(request)));
    }

    /** Đưa vào thùng rác (QTN-18); tài liệu đã gắn vẫn nguyên trong kho. */
    @DeleteMapping("/{id}")
    public TrashedResponse trash(@PathVariable UUID id, HttpServletRequest request) {
        return new TrashedResponse(workspaces.trash(id, ClientInfo.from(request)).getTrashBatchId());
    }

    /** Thư mục trong kho nhận tệp tải lên ngay tại không gian học; chưa có thì được tạo. */
    @PostMapping("/{id}/default-folder")
    public FolderResponse defaultFolder(@PathVariable UUID id, HttpServletRequest request) {
        return FolderResponse.of(workspaces.defaultFolder(id, ClientInfo.from(request)), 0);
    }

    public record TrashedResponse(UUID trashBatchId) {
    }
}
