package com.learningcompanion.assistant.api;

import com.learningcompanion.assistant.api.dto.ConversationResponse;
import com.learningcompanion.assistant.application.AnswerExportService;
import com.learningcompanion.assistant.application.ConversationService;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Đọc lại hội thoại với trợ lý; hội thoại của người khác trả 404 và lần từ chối được ghi nhật ký. */
@RestController
@RequestMapping(ApiPaths.ASSISTANT + "/conversations")
public class ConversationController {

    /** Kiểu tệp văn bản định dạng nhẹ. */
    static final MediaType MARKDOWN = new MediaType("text", "markdown", StandardCharsets.UTF_8);

    private final ConversationService conversations;
    private final AnswerExportService exports;

    public ConversationController(ConversationService conversations, AnswerExportService exports) {
        this.conversations = conversations;
        this.exports = exports;
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

    /**
     * Tải một câu trả lời về dạng văn bản định dạng nhẹ, có dấu nhận biết do trí tuệ nhân tạo sinh ra trong siêu dữ
     * liệu (NCL-04-CN-005 TC-02); tiêu đề {@code X-AI-Generated} báo cho phần mềm đọc. Bộ đính dấu lỗi thì trả 503,
     * không xuất tệp thiếu dấu (TC-04).
     */
    @GetMapping("/{id}/messages/{messageId}/export")
    public ResponseEntity<String> export(@PathVariable UUID id, @PathVariable UUID messageId,
                                         HttpServletRequest request) {
        AnswerExportService.Export export = exports.markdown(id, messageId, ClientInfo.from(request));
        return ResponseEntity.ok()
                .contentType(MARKDOWN)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(export.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-AI-Generated", "true")
                .body(export.content());
    }
}
