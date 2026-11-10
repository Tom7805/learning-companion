package com.learningcompanion.assistant.infrastructure;

import com.learningcompanion.ai.safety.api.SafetyStream;
import com.learningcompanion.ai.safety.application.SafeGenerationService;
import com.learningcompanion.assistant.api.dto.CitationResponse;
import com.learningcompanion.assistant.application.ConversationService;
import com.learningcompanion.assistant.application.GroundedAnswerService;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Luồng sự kiện của một câu trả lời (NCL-07-CN-001): "meta" (mã hội thoại, câu hỏi, câu trả lời và phạm vi tài liệu),
 * các "chunk" hiện dần, có thể kèm "safety", rồi "done" (trạng thái, câu hỏi gợi ý, trích dẫn, tài liệu nên chọn thêm)
 * hoặc "error" (mã lỗi và phần đã hiện). Trình duyệt đóng kết nối thì câu trả lời vẫn chạy hết và vẫn được lưu.
 */
public final class SseChatEmitter implements GroundedAnswerService.Listener {

    private final SseEmitter emitter = new SseEmitter(0L);

    public SseEmitter emitter() {
        return emitter;
    }

    public void meta(ConversationService.Turn turn) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("conversationId", turn.conversation().getId());
        body.put("workspaceId", turn.conversation().getWorkspaceId());
        body.put("title", turn.conversation().getTitle());
        body.put("questionId", turn.question().getId());
        body.put("answerId", turn.answer().getId());
        body.put("documentIds", turn.question().getDocumentIds());
        send("meta", body);
    }

    @Override
    public void chunk(String text) {
        send("chunk", Map.of("text", text));
    }

    @Override
    public void safety(SafeGenerationService.Outcome outcome) {
        send("safety", SafetyStream.safety(outcome));
    }

    @Override
    public void done(GroundedAnswerService.Answer answer) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", answer.status().name());
        body.put("text", answer.text());
        body.put("suggestions", answer.suggestions());
        body.put("citations", answer.citations().stream().map(CitationResponse::of).toList());
        body.put("elsewhere", answer.elsewhere().stream()
                .map(document -> Map.of("id", document.getId(), "title", document.getTitle())).toList());
        body.put("flaggedSources", answer.flaggedSources());
        send("done", body);
        emitter.complete();
    }

    @Override
    public void failed(String code, String message, String partial) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("partial", partial);
        send("error", body);
        emitter.complete();
    }

    private void send(String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            // Trình duyệt đã đóng kết nối; câu trả lời vẫn chạy hết và được lưu.
        }
    }
}
