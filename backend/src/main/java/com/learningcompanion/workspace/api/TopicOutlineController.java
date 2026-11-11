package com.learningcompanion.workspace.api;

import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import com.learningcompanion.workspace.api.dto.TopicRequest;
import com.learningcompanion.workspace.api.dto.TopicTreeResponse;
import com.learningcompanion.workspace.application.TopicOutlineService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dàn ý chủ đề của một không gian học (NCL-06-CN-002). Mọi thao tác trả lại cả cây để giao diện luôn hiển thị đúng thứ
 * tự sau khi máy chủ sắp xếp lại. Không gian học hay chủ đề của người khác trả 404 và lần từ chối được ghi nhật ký.
 */
@RestController
@RequestMapping(ApiPaths.WORKSPACES + "/{workspaceId}/topics")
public class TopicOutlineController {

    private final TopicOutlineService outline;

    public TopicOutlineController(TopicOutlineService outline) {
        this.outline = outline;
    }

    @GetMapping
    public TopicTreeResponse tree(@PathVariable UUID workspaceId, HttpServletRequest request) {
        return TopicTreeResponse.of(outline.outline(workspaceId, ClientInfo.from(request)));
    }

    @PostMapping
    public TopicTreeResponse create(@PathVariable UUID workspaceId, @Valid @RequestBody TopicRequest.Create body,
                                    HttpServletRequest request) {
        return TopicTreeResponse.of(outline.create(workspaceId, body.parentId(), body.title(), body.goal(),
                ClientInfo.from(request)));
    }

    /** Trợ lý đề xuất dàn ý từ các nguồn sẵn sàng; tốn lượt theo bảng quy đổi. */
    @PostMapping("/suggest")
    public TopicTreeResponse suggest(@PathVariable UUID workspaceId, HttpServletRequest request) {
        return TopicTreeResponse.of(outline.suggest(workspaceId, ClientInfo.from(request)));
    }

    @PatchMapping("/{topicId}")
    public TopicTreeResponse update(@PathVariable UUID workspaceId, @PathVariable UUID topicId,
                                    @Valid @RequestBody TopicRequest.Update body, HttpServletRequest request) {
        return TopicTreeResponse.of(outline.update(workspaceId, topicId, body.title(), body.goal(),
                ClientInfo.from(request)));
    }

    @PutMapping("/{topicId}/position")
    public TopicTreeResponse move(@PathVariable UUID workspaceId, @PathVariable UUID topicId,
                                  @RequestBody TopicRequest.Move body, HttpServletRequest request) {
        return TopicTreeResponse.of(outline.move(workspaceId, topicId, body.parentId(), body.index(),
                ClientInfo.from(request)));
    }

    @PostMapping("/{topicId}/merge")
    public TopicTreeResponse merge(@PathVariable UUID workspaceId, @PathVariable UUID topicId,
                                   @RequestBody TopicRequest.Merge body, HttpServletRequest request) {
        return TopicTreeResponse.of(outline.merge(workspaceId, topicId, body.intoId(), ClientInfo.from(request)));
    }

    @PostMapping("/{topicId}/split")
    public TopicTreeResponse split(@PathVariable UUID workspaceId, @PathVariable UUID topicId,
                                   @Valid @RequestBody TopicRequest.Split body, HttpServletRequest request) {
        return TopicTreeResponse.of(outline.split(workspaceId, topicId, body.title(), body.childIds(),
                ClientInfo.from(request)));
    }

    @DeleteMapping("/{topicId}")
    public TopicTreeResponse delete(@PathVariable UUID workspaceId, @PathVariable UUID topicId,
                                    HttpServletRequest request) {
        return TopicTreeResponse.of(outline.delete(workspaceId, topicId, ClientInfo.from(request)));
    }
}
