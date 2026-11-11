package com.learningcompanion.assistant.api.dto;

import com.learningcompanion.assistant.domain.Citation;
import com.learningcompanion.assistant.domain.Message;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Một tin nhắn của hội thoại kèm trích dẫn (với câu trả lời). */
public record MessageResponse(UUID id, String role, String content, String status, List<UUID> documentIds,
                              List<String> suggestions, String errorCode, List<CitationResponse> citations,
                              Instant createdAt) {

    /** {@code available}: các tài liệu được trích còn trong kho. */
    public static MessageResponse of(Message message, List<Citation> citations, Set<UUID> available) {
        return new MessageResponse(message.getId(), message.getRole().name(), message.getContent(),
                message.getStatus().name(), message.getDocumentIds(), message.getSuggestions(),
                message.getErrorCode(), citations.stream().map(citation -> CitationResponse.of(citation,
                        citation.getDocumentId() != null
                                && available.contains(citation.getDocumentId()), message.getContent())).toList(),
                message.getCreatedAt());
    }
}
