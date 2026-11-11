package com.learningcompanion.assistant.application;

import com.learningcompanion.ai.config.domain.AiFeature;
import com.learningcompanion.ai.labeling.AiLabelService;
import com.learningcompanion.ai.labeling.AiProvenance;
import com.learningcompanion.assistant.domain.Citation;
import com.learningcompanion.assistant.domain.CitationRepository;
import com.learningcompanion.assistant.domain.Conversation;
import com.learningcompanion.assistant.domain.Message;
import com.learningcompanion.assistant.domain.MessageRole;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.web.ClientInfo;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tải một câu trả lời của trợ lý về dạng văn bản định dạng nhẹ (NCL-04-CN-005 TC-02): nội dung câu trả lời, danh sách
 * tài liệu đã trích, và dấu nhận biết do trí tuệ nhân tạo sinh ra trong siêu dữ liệu qua {@link AiLabelService}. Bộ
 * đính dấu lỗi thì không xuất tệp (TC-04).
 */
@Service
public class AnswerExportService {

    private final ConversationService conversations;
    private final CitationRepository citations;
    private final AiLabelService labels;

    public AnswerExportService(ConversationService conversations, CitationRepository citations,
                               AiLabelService labels) {
        this.conversations = conversations;
        this.citations = citations;
        this.labels = labels;
    }

    /** Tệp tải về: tên gợi ý và nội dung đã có dấu nhận biết. */
    public record Export(String fileName, String content) {
    }

    @Transactional(readOnly = true)
    public Export markdown(UUID conversationId, UUID messageId, ClientInfo client) {
        Conversation conversation = conversations.require(conversationId, client);
        Message answer = conversations.requireAnswer(conversationId, messageId, client);
        if (!answer.isAiGenerated()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        String title = conversation.getTitle() == null || conversation.getTitle().isBlank()
                ? "Câu trả lời của trợ lý" : conversation.getTitle();
        StringBuilder body = new StringBuilder("# ").append(title.strip()).append("\n\n")
                .append(answer.getContent().strip()).append('\n');
        List<Citation> cited = citations.findAllByOwnerIdAndMessageIdInOrderByNumberAsc(answer.getOwnerId(),
                        List.of(answer.getId()))
                .stream().toList();
        if (!cited.isEmpty()) {
            body.append("\n## Nguồn\n\n");
            Map<Integer, Citation> byNumber = new LinkedHashMap<>();
            cited.forEach(citation -> byNumber.putIfAbsent(citation.getNumber(), citation));
            byNumber.values().forEach(citation -> body.append(citation.getNumber()).append(". ")
                    .append(citation.getDocumentTitle()).append('\n'));
        }
        AiProvenance provenance = AiProvenance.generated(answer.getId(), AiFeature.ASSISTANT_CHAT.name(),
                answer.getCreatedAt());
        return new Export(fileName(title), labels.markdown(title, body.toString(), provenance));
    }

    /**
     * Tải cả hội thoại về dạng văn bản định dạng nhẹ (NCL-07-CN-003): từng câu hỏi và câu trả lời theo thứ tự, mỗi câu
     * trả lời kèm danh sách nguồn đã trích (tên tài liệu và vị trí). Có câu trả lời do trí tuệ nhân tạo sinh ra nên tệp
     * luôn mang dấu nhận biết; bộ đính dấu lỗi thì không xuất tệp.
     */
    @Transactional(readOnly = true)
    public Export conversation(UUID conversationId, ClientInfo client) {
        ConversationService.Thread thread = conversations.thread(conversationId, client);
        Conversation conversation = thread.conversation();
        String title = conversation.getTitle() == null || conversation.getTitle().isBlank()
                ? "Hội thoại với trợ lý" : conversation.getTitle().strip();
        StringBuilder body = new StringBuilder("# ").append(title).append('\n');
        for (Message message : thread.messages()) {
            if (message.getRole() == MessageRole.USER) {
                body.append("\n## Câu hỏi\n\n").append(message.getContent().strip()).append('\n');
                continue;
            }
            if (!message.isAiGenerated()) {
                continue;
            }
            body.append("\n## Trả lời\n\n").append(message.getContent().strip()).append('\n');
            List<Citation> cited = thread.citations().getOrDefault(message.getId(), List.of());
            if (!cited.isEmpty()) {
                body.append("\n**Nguồn**\n\n");
                Map<Integer, Citation> byNumber = new LinkedHashMap<>();
                cited.forEach(citation -> byNumber.putIfAbsent(citation.getNumber(), citation));
                byNumber.values().forEach(citation -> body.append(citation.getNumber()).append(". ")
                        .append(citation.getDocumentTitle()).append(" · ")
                        .append(GroundedAnswerService.locationLabel(citation.getLocation())).append('\n'));
            }
        }
        AiProvenance provenance = AiProvenance.generated(conversation.getId(), AiFeature.ASSISTANT_CHAT.name(),
                conversation.getCreatedAt());
        return new Export(fileName(title), labels.markdown(title, body.toString(), provenance));
    }

    /** Tên tệp từ tiêu đề hội thoại: bỏ ký tự không dùng được trong tên tệp, tối đa 80 ký tự. */
    static String fileName(String title) {
        String clean = title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ").replaceAll("\\s+", " ").strip();
        if (clean.isEmpty()) {
            clean = "Câu trả lời của trợ lý";
        }
        if (clean.length() > 80) {
            clean = clean.substring(0, 80).strip();
        }
        return clean + ".md";
    }
}
