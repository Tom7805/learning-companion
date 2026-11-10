package com.learningcompanion.assistant.api.dto;

import com.learningcompanion.assistant.domain.Citation;
import com.learningcompanion.assistant.domain.Message;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Một tin nhắn của hội thoại kèm trích dẫn (với câu trả lời). */
public record MessageResponse(UUID id, String role, String content, String status, List<UUID> documentIds,
                              List<String> suggestions, String errorCode, List<CitationResponse> citations,
                              Instant createdAt) {

    public static MessageResponse of(Message message, List<Citation> citations) {
        return new MessageResponse(message.getId(), message.getRole().name(), message.getContent(),
                message.getStatus().name(), message.getDocumentIds(), message.getSuggestions(),
                message.getErrorCode(), citations.stream().map(CitationResponse::of).toList(),
                message.getCreatedAt());
    }
}
