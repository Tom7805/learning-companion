package com.learningcompanion.assistant.api;

import com.learningcompanion.assistant.api.dto.ConversationResponse;
import com.learningcompanion.assistant.application.ConversationService;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Đọc lại hội thoại với trợ lý; hội thoại của người khác trả 404 và lần từ chối được ghi nhật ký. */
@RestController
@RequestMapping(ApiPaths.ASSISTANT + "/conversations")
public class ConversationController {

    private final ConversationService conversations;

    public ConversationController(ConversationService conversations) {
        this.conversations = conversations;
    }

    /** Hội thoại của không gian học, hoặc hội thoại riêng khi không có {@code workspaceId}; mới dùng trước. */
    @GetMapping
    public List<ConversationResponse.Summary> list(@RequestParam(required = false) UUID workspaceId,
                                                   HttpServletRequest request) {
        return conversations.list(workspaceId, ClientInfo.from(request)).stream()
                .map(ConversationResponse.Summary::of).toList();
    }

    @GetMapping("/{id}")
    public ConversationResponse get(@PathVariable UUID id, HttpServletRequest request) {
        return ConversationResponse.of(conversations.thread(id, ClientInfo.from(request)));
    }
}
