package com.learningcompanion.assistant.application;

import com.learningcompanion.assistant.domain.Citation;
import com.learningcompanion.assistant.domain.CitationRepository;
import com.learningcompanion.assistant.domain.Conversation;
import com.learningcompanion.assistant.domain.ConversationRepository;
import com.learningcompanion.assistant.domain.Message;
import com.learningcompanion.assistant.domain.MessageRepository;
import com.learningcompanion.shared.export.DataExportContributor;
import com.learningcompanion.shared.export.ExportFormat;
import com.learningcompanion.shared.export.ExportWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phần "Hội thoại với trợ lý" của gói xuất (NCL-07-CN-001): mỗi hội thoại kèm câu hỏi, câu trả lời, câu hỏi gợi ý và
 * các đoạn đã trích (tên tài liệu, vị trí, đoạn gốc).
 */
@Component
public class AssistantExportContributor implements DataExportContributor {

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final CitationRepository citations;

    public AssistantExportContributor(ConversationRepository conversations, MessageRepository messages,
                                      CitationRepository citations) {
        this.conversations = conversations;
        this.messages = messages;
        this.citations = citations;
    }

    @Override
    public String section() {
        return "assistant";
    }

    @Override
    public String title() {
        return "Hội thoại với trợ lý";
    }

    @Override
    public List<String> describe() {
        return List.of("conversations.json: các hội thoại (không gian học, tên, thời điểm) với câu hỏi, câu trả lời, "
                + "câu hỏi gợi ý và các đoạn tài liệu đã trích.");
    }

    @Override
    public List<ExportFormat> formats() {
        return List.of(ExportFormat.JSON);
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(UUID ownerId, ExportWriter writer) {
        List<Conversation> mine = conversations.findAllByOwnerIdOrderByCreatedAtAsc(ownerId);
        if (mine.isEmpty()) {
            return;
        }
        List<Message> all = messages.findAllByOwnerIdOrderByCreatedAtAsc(ownerId);
        Map<UUID, List<Citation>> cited = citations.findAllByOwnerIdAndMessageIdInOrderByNumberAsc(ownerId,
                all.stream().map(Message::getId).toList()).stream().collect(Collectors.groupingBy(Citation::getMessageId));
        Map<UUID, List<Map<String, Object>>> byConversation = new LinkedHashMap<>();
        for (Message message : all) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("role", message.getRole().name());
            entry.put("status", message.getStatus().name());
            entry.put("content", message.getContent());
            entry.put("createdAt", message.getCreatedAt());
            if (!message.getSuggestions().isEmpty()) {
                entry.put("suggestions", message.getSuggestions());
            }
            List<Map<String, Object>> sources = new ArrayList<>();
            for (Citation citation : cited.getOrDefault(message.getId(), List.of())) {
                Map<String, Object> source = new LinkedHashMap<>();
                source.put("number", citation.getNumber());
                source.put("document", citation.getDocumentTitle());
                source.put("location", citation.getLocation().kind().name() + " " + citation.getLocation().number());
                source.put("excerpt", citation.getExcerpt());
                sources.add(source);
            }
            if (!sources.isEmpty()) {
                entry.put("citations", sources);
            }
            byConversation.computeIfAbsent(message.getConversationId(), key -> new ArrayList<>()).add(entry);
        }
        List<Map<String, Object>> index = new ArrayList<>();
        for (Conversation conversation : mine) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", conversation.getId());
            entry.put("workspaceId", conversation.getWorkspaceId());
            entry.put("title", conversation.getTitle());
            entry.put("createdAt", conversation.getCreatedAt());
            entry.put("updatedAt", conversation.getUpdatedAt());
            entry.put("messages", byConversation.getOrDefault(conversation.getId(), List.of()));
            index.add(entry);
        }
        writer.json("conversations.json", index);
    }
}
