package com.learningcompanion.assistant.application;

import com.learningcompanion.assistant.domain.Citation;
import com.learningcompanion.assistant.domain.CitationRepository;
import com.learningcompanion.assistant.domain.Conversation;
import com.learningcompanion.assistant.domain.ConversationRepository;
import com.learningcompanion.assistant.domain.Message;
import com.learningcompanion.assistant.domain.MessageRepository;
import com.learningcompanion.assistant.domain.MessageRole;
import com.learningcompanion.library.application.DocumentService;
import com.learningcompanion.library.domain.Document;
import com.learningcompanion.library.domain.DocumentRepository;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.ownership.OwnerContext;
import com.learningcompanion.shared.ownership.OwnershipGuard;
import com.learningcompanion.shared.web.ClientInfo;
import com.learningcompanion.workspace.application.WorkspaceDocumentLinkService;
import com.learningcompanion.workspace.application.WorkspaceService;
import com.learningcompanion.workspace.domain.Workspace;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hội thoại với trợ lý (NCL-07-CN-001): xác định phạm vi tài liệu của câu hỏi, lưu câu hỏi và câu trả lời, đọc lại hội
 * thoại. Trong không gian học, phạm vi mặc định là mọi tài liệu đang gắn; người học bỏ chọn bớt được nhưng không thêm
 * được tài liệu ngoài không gian học (QTN-22). Hội thoại, không gian học hay tài liệu của người khác đều trả 404 và lần
 * từ chối được ghi nhật ký (TC-06).
 */
@Service
public class ConversationService {

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final CitationRepository citations;
    private final DocumentRepository documentRepository;
    private final DocumentService documents;
    private final WorkspaceService workspaces;
    private final WorkspaceDocumentLinkService links;
    private final OwnerContext owner;
    private final OwnershipGuard guard;
    private final Clock clock;

    public ConversationService(ConversationRepository conversations, MessageRepository messages,
                               CitationRepository citations, DocumentRepository documentRepository,
                               DocumentService documents, WorkspaceService workspaces,
                               WorkspaceDocumentLinkService links, OwnerContext owner, OwnershipGuard guard,
                               Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.citations = citations;
        this.documentRepository = documentRepository;
        this.documents = documents;
        this.workspaces = workspaces;
        this.links = links;
        this.owner = owner;
        this.guard = guard;
        this.clock = clock;
    }

    /** Một lần hỏi đã được nhận: hội thoại, câu hỏi, câu trả lời đang chờ và các tài liệu trong phạm vi. */
    public record Turn(Conversation conversation, Message question, Message answer, List<Document> scope,
                       boolean selectedAny) {
    }

    /**
     * Nhận một câu hỏi: tìm hoặc tạo hội thoại, xác định phạm vi tài liệu, lưu câu hỏi và chỗ cho câu trả lời.
     *
     * @param requested tài liệu người học chọn; trống: mặc định (mọi tài liệu của không gian học, hay không có gì ở
     *                  hội thoại riêng)
     */
    @Transactional
    public Turn ask(UUID conversationId, UUID workspaceId, String question, List<UUID> requested, ClientInfo client) {
        UUID ownerId = owner.requireOwnerId();
        Conversation conversation = conversationId == null ? null : require(conversationId, client);
        UUID scopeWorkspace = conversation != null ? conversation.getWorkspaceId() : workspaceId;
        if (conversation != null && workspaceId != null && !workspaceId.equals(scopeWorkspace)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        List<UUID> selected = selection(scopeWorkspace, requested, client);
        Instant now = clock.instant();
        if (conversation == null) {
            conversation = conversations.save(Conversation.start(ownerId, scopeWorkspace, question, now));
        } else {
            conversation.touch(now);
        }
        Message asked = messages.save(Message.question(ownerId, conversation.getId(), question, selected, now));
        Message answer = messages.save(Message.answer(ownerId, conversation.getId(), now.plusMillis(1)));
        return new Turn(conversation, asked, answer, ready(ownerId, selected), !selected.isEmpty());
    }

    /**
     * Trả lời lại câu hỏi cuối của hội thoại: câu trả lời cũ được thay bằng câu trả lời mới, cùng phạm vi tài liệu (bỏ
     * tài liệu đã bị xóa từ đó tới giờ).
     */
    @Transactional
    public Turn regenerate(UUID conversationId, ClientInfo client) {
        Conversation conversation = require(conversationId, client);
        if (conversation.getWorkspaceId() != null) {
            workspaces.require(conversation.getWorkspaceId(), client);
        }
        List<Message> thread = messages.findAllByOwnerIdAndConversationIdOrderByCreatedAtAsc(
                conversation.getOwnerId(), conversation.getId());
        Message last = null;
        for (Message message : thread) {
            if (message.getRole() == MessageRole.USER) {
                last = message;
            }
        }
        if (last == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Message question = last;
        thread.stream().filter(message -> message.getRole() == MessageRole.ASSISTANT
                        && message.getCreatedAt().isAfter(question.getCreatedAt()))
                .forEach(messages::delete);
        Instant now = clock.instant();
        conversation.touch(now);
        Message answer = messages.save(Message.answer(conversation.getOwnerId(), conversation.getId(), now));
        return new Turn(conversation, question, answer, ready(conversation.getOwnerId(), question.getDocumentIds()),
                !question.getDocumentIds().isEmpty());
    }

    /** Lưu kết quả của câu trả lời cùng các trích dẫn; chạy trên luồng nền nên nhận chủ sở hữu trực tiếp. */
    @Transactional
    public void complete(UUID ownerId, UUID answerId, Message.Status status, String text, List<String> suggestions,
                         String error, List<Citation> cited) {
        Message answer = messages.findByIdAndOwnerId(answerId, ownerId).orElse(null);
        if (answer == null) {
            // Câu trả lời đã bị thay (người học bấm trả lời lại) trong lúc đang sinh.
            return;
        }
        answer.finish(status, text, suggestions, error, clock.instant());
        cited.forEach(citations::save);
    }

    /** Hội thoại của không gian học (hoặc hội thoại riêng khi {@code workspaceId} trống), mới dùng trước. */
    @Transactional(readOnly = true)
    public List<Conversation> list(UUID workspaceId, ClientInfo client) {
        UUID ownerId = owner.requireOwnerId();
        if (workspaceId == null) {
            return conversations.findTop50ByOwnerIdAndWorkspaceIdIsNullAndTrashedAtIsNullOrderByUpdatedAtDesc(ownerId);
        }
        Workspace workspace = workspaces.require(workspaceId, client);
        return conversations.findTop50ByOwnerIdAndWorkspaceIdAndTrashedAtIsNullOrderByUpdatedAtDesc(ownerId,
                workspace.getId());
    }

    /** Hội thoại đầy đủ: tin nhắn theo thứ tự, trích dẫn của từng câu trả lời và tài liệu của phạm vi gần nhất. */
    @Transactional(readOnly = true)
    public Thread thread(UUID conversationId, ClientInfo client) {
        Conversation conversation = require(conversationId, client);
        if (conversation.getWorkspaceId() != null) {
            workspaces.require(conversation.getWorkspaceId(), client);
        }
        List<Message> thread = messages.findAllByOwnerIdAndConversationIdOrderByCreatedAtAsc(
                conversation.getOwnerId(), conversation.getId());
        Map<UUID, List<Citation>> cited = citations.findAllByOwnerIdAndMessageIdInOrderByNumberAsc(
                        conversation.getOwnerId(), thread.stream().map(Message::getId).toList()).stream()
                .collect(Collectors.groupingBy(Citation::getMessageId, LinkedHashMap::new, Collectors.toList()));
        List<UUID> lastScope = thread.stream().filter(message -> message.getRole() == MessageRole.USER)
                .reduce((first, second) -> second).map(Message::getDocumentIds).orElse(List.of());
        List<Document> scope = documentRepository.findAllByOwnerIdAndIdIn(conversation.getOwnerId(), lastScope)
                .stream().filter(document -> !document.isTrashed()).toList();
        return new Thread(conversation, thread, cited, scope);
    }

    /** Một hội thoại cùng tin nhắn, trích dẫn theo câu trả lời và các tài liệu còn lại của phạm vi gần nhất. */
    public record Thread(Conversation conversation, List<Message> messages, Map<UUID, List<Citation>> citations,
                         List<Document> scope) {
    }

    /** Hội thoại của người đang đăng nhập; của người khác thì 404 và lần từ chối được ghi nhật ký. */
    @Transactional(readOnly = true)
    public Conversation require(UUID id, ClientInfo client) {
        Conversation conversation = guard.require(conversations, id, AuditObjectType.CONVERSATION, client);
        if (conversation.isTrashed()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return conversation;
    }

    /** Câu trả lời của người đang đăng nhập, để dừng giữa chừng. */
    @Transactional(readOnly = true)
    public Message requireAnswer(UUID conversationId, UUID messageId, ClientInfo client) {
        Conversation conversation = require(conversationId, client);
        return messages.findByIdAndOwnerId(messageId, conversation.getOwnerId())
                .filter(message -> message.getConversationId().equals(conversation.getId())
                        && message.getRole() == MessageRole.ASSISTANT)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /** Các tài liệu được chọn làm phạm vi, đều phải thuộc người học (và thuộc không gian học, nếu có). */
    private List<UUID> selection(UUID workspaceId, List<UUID> requested, ClientInfo client) {
        if (workspaceId != null) {
            Set<UUID> attached = links.documents(workspaceId, client).stream()
                    .map(item -> item.document().getId()).collect(Collectors.toCollection(LinkedHashSet::new));
            if (requested == null) {
                return List.copyOf(attached);
            }
            Set<UUID> chosen = new LinkedHashSet<>(requested);
            if (!attached.containsAll(chosen)) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            return List.copyOf(chosen);
        }
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        return documents.requireAll(requested, client).stream().map(Document::getId).toList();
    }

    /** Tài liệu trong phạm vi còn trong kho, theo thứ tự đã chọn. */
    private List<Document> ready(UUID ownerId, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, Document> byId = new LinkedHashMap<>();
        documentRepository.findAllByOwnerIdAndIdIn(ownerId, ids).stream().filter(document -> !document.isTrashed())
                .forEach(document -> byId.put(document.getId(), document));
        List<Document> ordered = new ArrayList<>();
        ids.stream().map(byId::get).filter(Objects::nonNull).forEach(ordered::add);
        return ordered;
    }
}
