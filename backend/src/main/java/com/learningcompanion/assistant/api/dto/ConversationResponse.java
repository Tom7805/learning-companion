package com.learningcompanion.assistant.api.dto;

import com.learningcompanion.assistant.application.ConversationService;
import com.learningcompanion.assistant.domain.Conversation;
import com.learningcompanion.library.domain.Document;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Hội thoại đầy đủ: tin nhắn theo thứ tự và các tài liệu (còn trong kho) của phạm vi gần nhất. */
public record ConversationResponse(UUID id, UUID workspaceId, String title, Instant createdAt, Instant updatedAt,
                                   List<MessageResponse> messages, List<ScopeDocument> scope) {

    /** Tài liệu trong phạm vi: tên, loại và trạng thái xử lý để giao diện hiện thẻ chọn. */
    public record ScopeDocument(UUID id, String title, String type, String status) {

        public static ScopeDocument of(Document document) {
            return new ScopeDocument(document.getId(), document.getTitle(), document.getType().name(),
                    document.getStatus().name());
        }
    }

    /** Một dòng trong danh sách hội thoại. */
    public record Summary(UUID id, UUID workspaceId, String title, Instant updatedAt) {

        public static Summary of(Conversation conversation) {
            return new Summary(conversation.getId(), conversation.getWorkspaceId(), conversation.getTitle(),
                    conversation.getUpdatedAt());
        }
    }

    public static ConversationResponse of(ConversationService.Thread thread) {
        Conversation conversation = thread.conversation();
        return new ConversationResponse(conversation.getId(), conversation.getWorkspaceId(), conversation.getTitle(),
                conversation.getCreatedAt(), conversation.getUpdatedAt(),
                thread.messages().stream().map(message -> MessageResponse.of(message,
                        thread.citations().getOrDefault(message.getId(), List.of()))).toList(),
                thread.scope().stream().map(ScopeDocument::of).toList());
    }
}
