package com.learningcompanion.assistant.api;

import com.learningcompanion.assistant.api.dto.ConversationResponse;
import com.learningcompanion.assistant.api.dto.ConversationUpdateRequest;
import com.learningcompanion.assistant.application.AnswerExportService;
import com.learningcompanion.assistant.application.ConversationService;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /**
     * Lịch sử hội thoại của không gian học, hoặc hội thoại riêng khi không có {@code workspaceId}: đã ghim lên trước
     * rồi mới dùng trước; {@code q} tìm theo tên và nội dung (NCL-07-CN-003).
     */
    @GetMapping
    public List<ConversationResponse.Summary> list(@RequestParam(required = false) UUID workspaceId,
                                                   @RequestParam(required = false) String q,
                                                   HttpServletRequest request) {
        return conversations.list(workspaceId, q, ClientInfo.from(request)).stream()
                .map(ConversationResponse.Summary::of).toList();
    }

    @GetMapping("/{id}")
    public ConversationResponse get(@PathVariable UUID id, HttpServletRequest request) {
        return ConversationResponse.of(conversations.thread(id, ClientInfo.from(request)));
    }

    /** Đổi tên, ghim hay bỏ ghim hội thoại. */
    @PatchMapping("/{id}")
    public ConversationResponse.Summary update(@PathVariable UUID id,
                                               @Valid @RequestBody ConversationUpdateRequest body,
                                               HttpServletRequest request) {
        return ConversationResponse.Summary.of(conversations.update(id,
                new ConversationService.Change(body.title(), body.pinned(), false, null), ClientInfo.from(request)));
    }

    /** Chuyển hội thoại vào không gian học (TC-04): từ lượt hỏi sau, phạm vi tài liệu theo không gian học đó. */
    @PutMapping("/{id}/workspace")
    public ConversationResponse.Summary move(@PathVariable UUID id, @RequestBody ConversationUpdateRequest.Move body,
                                             HttpServletRequest request) {
        return ConversationResponse.Summary.of(conversations.update(id,
                new ConversationService.Change(null, null, true, body.workspaceId()), ClientInfo.from(request)));
    }

    /** Bỏ hội thoại vào thùng rác. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest request) {
        conversations.trash(id, ClientInfo.from(request));
        return ResponseEntity.noContent().build();
    }

    /** Tải cả hội thoại về dạng văn bản định dạng nhẹ, kèm nguồn của từng câu trả lời và dấu nhận biết nội dung AI. */
    @GetMapping("/{id}/export")
    public ResponseEntity<String> exportConversation(@PathVariable UUID id, HttpServletRequest request) {
        return markdown(exports.conversation(id, ClientInfo.from(request)));
    }

    /**
     * Tải một câu trả lời về dạng văn bản định dạng nhẹ, có dấu nhận biết do trí tuệ nhân tạo sinh ra trong siêu dữ
     * liệu (NCL-04-CN-005 TC-02); tiêu đề {@code X-AI-Generated} báo cho phần mềm đọc. Bộ đính dấu lỗi thì trả 503,
     * không xuất tệp thiếu dấu (TC-04).
     */
    @GetMapping("/{id}/messages/{messageId}/export")
    public ResponseEntity<String> export(@PathVariable UUID id, @PathVariable UUID messageId,
                                         HttpServletRequest request) {
        return markdown(exports.markdown(id, messageId, ClientInfo.from(request)));
    }

    private static ResponseEntity<String> markdown(AnswerExportService.Export export) {
        return ResponseEntity.ok()
                .contentType(MARKDOWN)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(export.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-AI-Generated", "true")
                .body(export.content());
    }
}
