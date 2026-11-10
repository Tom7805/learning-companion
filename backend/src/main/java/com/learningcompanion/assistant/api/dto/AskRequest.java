package com.learningcompanion.assistant.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Một câu hỏi cho trợ lý. {@code conversationId} trống: mở hội thoại mới (thuộc {@code workspaceId} nếu có).
 * {@code documentIds} trống (null): phạm vi mặc định, là mọi tài liệu của không gian học; danh sách rỗng là bỏ chọn hết.
 */
public record AskRequest(UUID conversationId, UUID workspaceId,
                         @NotBlank @Size(max = AskRequest.QUESTION_MAX) String question,
                         @Size(max = 200) List<UUID> documentIds) {

    public static final int QUESTION_MAX = 4000;
}
