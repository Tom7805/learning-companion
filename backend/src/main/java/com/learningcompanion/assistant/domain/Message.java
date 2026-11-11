package com.learningcompanion.assistant.domain;

import com.learningcompanion.shared.ownership.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Một tin nhắn trong hội thoại (NCL-07-CN-001). Câu hỏi giữ các tài liệu đang chọn lúc hỏi; câu trả lời giữ nội dung đã
 * hiện cho người học (kể cả phần đã hiện trước khi lỗi hay bị dừng), ba câu hỏi gợi ý và trạng thái.
 */
@Entity
@Table(name = "assistant_messages")
public class Message implements OwnedEntity {

    /** Trạng thái của tin nhắn. */
    public enum Status {
        /** Câu hỏi đã gửi. */
        SENT,
        /** Câu trả lời đang hiện dần. */
        STREAMING,
        COMPLETE,
        /** Người học bấm dừng: giữ phần đã hiện. */
        STOPPED,
        /** Dịch vụ lỗi giữa chừng: giữ phần đã hiện, lượt được hoàn. */
        FAILED,
        /** Bộ lọc an toàn chặn câu hỏi hay câu trả lời. */
        BLOCKED,
        /** Câu hỏi có dấu hiệu tự gây hại: nhận lời ân cần kèm nguồn trợ giúp. */
        SUPPORT,
        /** Tài liệu đang chọn không có thông tin: trợ lý nói rõ, không gọi mô hình. */
        NOT_FOUND,
        /** Chưa chọn tài liệu nào: trợ lý nhắc chọn nguồn. */
        NO_SOURCES
    }

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "document_ids", nullable = false, columnDefinition = "uuid[]")
    private UUID[] documentIds = new UUID[0];

    @Column(nullable = false, columnDefinition = "text")
    private String suggestions = "";

    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Message() {
    }

    public static Message question(UUID ownerId, UUID conversationId, String text, List<UUID> documentIds,
                                   Instant now) {
        Message message = create(ownerId, conversationId, MessageRole.USER, Status.SENT, now);
        message.content = text;
        message.documentIds = documentIds.toArray(UUID[]::new);
        return message;
    }

    /** Câu trả lời đang hiện dần. */
    public static Message answer(UUID ownerId, UUID conversationId, Instant now) {
        return create(ownerId, conversationId, MessageRole.ASSISTANT, Status.STREAMING, now);
    }

    private static Message create(UUID ownerId, UUID conversationId, MessageRole role, Status status, Instant now) {
        Message message = new Message();
        message.id = UUID.randomUUID();
        message.ownerId = ownerId;
        message.conversationId = conversationId;
        message.role = role;
        message.status = status;
        message.content = "";
        message.createdAt = now;
        message.updatedAt = now;
        return message;
    }

    public void finish(Status newStatus, String text, List<String> newSuggestions, String error, Instant now) {
        this.status = newStatus;
        this.content = text == null ? "" : text;
        this.suggestions = newSuggestions == null ? "" : String.join("\n", newSuggestions);
        this.errorCode = error;
        this.updatedAt = now;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public MessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public Status getStatus() {
        return status;
    }

    public List<UUID> getDocumentIds() {
        return documentIds == null ? List.of() : List.copyOf(Arrays.asList(documentIds));
    }

    public List<String> getSuggestions() {
        return suggestions == null || suggestions.isBlank() ? List.of() : List.of(suggestions.split("\n"));
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
