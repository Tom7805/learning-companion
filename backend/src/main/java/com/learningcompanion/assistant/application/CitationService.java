package com.learningcompanion.assistant.application;

import com.learningcompanion.assistant.domain.Citation;
import com.learningcompanion.assistant.domain.CitationRepository;
import com.learningcompanion.assistant.domain.Conversation;
import com.learningcompanion.assistant.domain.ConversationRepository;
import com.learningcompanion.assistant.domain.Message;
import com.learningcompanion.assistant.domain.MessageRepository;
import com.learningcompanion.library.domain.Document;
import com.learningcompanion.library.domain.DocumentRepository;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.ownership.OwnershipGuard;
import com.learningcompanion.shared.web.ClientInfo;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xem nhanh một trích dẫn (NCL-07-CN-002): tên tài liệu, vị trí, đoạn gốc và phần liên quan để tô sáng. Tài liệu đã bị
 * xóa (hẳn hay vào thùng rác) thì báo "tài liệu không còn" thay vì mở khung xem lỗi (TC-03). Trích dẫn của người khác
 * trả 404 và lần từ chối được ghi nhật ký (TC-04).
 */
@Service
public class CitationService {

    private final CitationRepository citations;
    private final MessageRepository messages;
    private final ConversationRepository conversations;
    private final DocumentRepository documents;
    private final OwnershipGuard guard;

    public CitationService(CitationRepository citations, MessageRepository messages,
                           ConversationRepository conversations, DocumentRepository documents, OwnershipGuard guard) {
        this.citations = citations;
        this.messages = messages;
        this.conversations = conversations;
        this.documents = documents;
        this.guard = guard;
    }

    /** Trích dẫn cùng việc tài liệu còn mở được không và phần cần tô sáng. */
    public record Preview(Citation citation, UUID conversationId, boolean available,
                          CitationHighlighter.Range highlight) {
    }

    @Transactional(readOnly = true)
    public Preview preview(UUID id, ClientInfo client) {
        Citation citation = guard.require(citations, id, AuditObjectType.CITATION, client);
        Message answer = messages.findByIdAndOwnerId(citation.getMessageId(), citation.getOwnerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Conversation conversation = conversations.findByIdAndOwnerId(answer.getConversationId(),
                        citation.getOwnerId()).filter(found -> !found.isTrashed())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        boolean available = !available(citation.getOwnerId(), Set.of(citation)).isEmpty();
        return new Preview(citation, conversation.getId(), available,
                CitationHighlighter.highlight(answer.getContent(), citation.getNumber(), citation.getExcerpt()));
    }

    /** Mã các tài liệu (trong số được trích) còn trong kho, để giao diện biết trích dẫn nào còn mở được. */
    @Transactional(readOnly = true)
    public Set<UUID> available(UUID ownerId, Collection<Citation> cited) {
        Set<UUID> ids = cited.stream().map(Citation::getDocumentId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Set.of();
        }
        return documents.findAllByOwnerIdAndIdIn(ownerId, ids).stream().filter(document -> !document.isTrashed())
                .map(Document::getId).collect(Collectors.toSet());
    }
}
