package com.learningcompanion.assistant.api;

import com.learningcompanion.assistant.api.dto.AskRequest;
import com.learningcompanion.assistant.application.ConversationService;
import com.learningcompanion.assistant.application.GroundedAnswerService;
import com.learningcompanion.assistant.infrastructure.SseChatEmitter;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Hỏi trợ lý trên tài liệu đang chọn (NCL-07-CN-001). Câu hỏi được nhận và kiểm quyền ngay (hội thoại, không gian học
 * hay tài liệu của người khác trả 404 trước khi có luồng sự kiện), rồi câu trả lời hiện dần qua luồng sự kiện.
 */
@RestController
@RequestMapping(ApiPaths.ASSISTANT)
public class ChatController {

    private final ConversationService conversations;
    private final GroundedAnswerService answers;

    public ChatController(ConversationService conversations, GroundedAnswerService answers) {
        this.conversations = conversations;
        this.answers = answers;
    }

    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@Valid @RequestBody AskRequest body, HttpServletRequest request) {
        ClientInfo client = ClientInfo.from(request);
        ConversationService.Turn turn = conversations.ask(body.conversationId(), body.workspaceId(),
                body.question().strip(), body.documentIds(), client);
        return stream(turn, client);
    }

    /** Trả lời lại câu hỏi cuối của hội thoại, ví dụ sau khi dịch vụ lỗi giữa chừng (TC-05). */
    @PostMapping(value = "/conversations/{id}/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerate(@PathVariable UUID id, HttpServletRequest request) {
        ClientInfo client = ClientInfo.from(request);
        return stream(conversations.regenerate(id, client), client);
    }

    /** Dừng câu trả lời đang hiện: phần đã hiện được giữ. */
    @PostMapping("/conversations/{id}/messages/{messageId}/stop")
    public ResponseEntity<Void> stop(@PathVariable UUID id, @PathVariable UUID messageId, HttpServletRequest request) {
        answers.stop(conversations.requireAnswer(id, messageId, ClientInfo.from(request)).getId());
        return ResponseEntity.noContent().build();
    }

    private SseEmitter stream(ConversationService.Turn turn, ClientInfo client) {
        SseChatEmitter emitter = new SseChatEmitter();
        emitter.meta(turn);
        Thread.ofVirtual().name("assistant-answer-" + turn.answer().getId())
                .start(() -> answers.answer(turn, client, emitter));
        return emitter.emitter();
    }
}
