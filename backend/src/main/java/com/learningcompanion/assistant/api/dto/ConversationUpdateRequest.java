package com.learningcompanion.assistant.api.dto;

import com.learningcompanion.assistant.domain.Conversation;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Đổi tên hay ghim một hội thoại (NCL-07-CN-003); trường để trống (null) thì giữ nguyên. */
public record ConversationUpdateRequest(@Size(max = Conversation.TITLE_MAX_LENGTH) String title, Boolean pinned) {

    /** Chuyển hội thoại vào một không gian học; {@code workspaceId} trống thì thành hội thoại riêng. */
    public record Move(UUID workspaceId) {
    }
}
