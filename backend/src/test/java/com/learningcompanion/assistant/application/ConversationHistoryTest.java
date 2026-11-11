package com.learningcompanion.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.learningcompanion.ai.gateway.model.ChatRequest;
import com.learningcompanion.assistant.domain.Conversation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** NCL-07-CN-003 — mẫu tìm hội thoại, các lượt trước gửi cho mô hình, ghim và đổi tên. */
class ConversationHistoryTest {

    @Test
    @DisplayName("Từ khóa tìm được gọn khoảng trắng, viết thường và thoát ký tự đại diện")
    void likePattern() {
        assertThat(ConversationService.likePattern(null)).isEqualTo("%");
        assertThat(ConversationService.likePattern("   ")).isEqualTo("%");
        assertThat(ConversationService.likePattern("  Đồ   Thị ")).isEqualTo("%đồ thị%");
        assertThat(ConversationService.likePattern("50%_a!")).isEqualTo("%50!%!_a!!%");
        assertThat(ConversationService.likePattern("x".repeat(300))).hasSize(ConversationService.QUERY_MAX_LENGTH + 2);
    }

    @Test
    @DisplayName("Các lượt trước thành cặp hỏi và đáp, bỏ số trích dẫn cũ")
    void historyMessages() {
        List<ChatRequest.Message> turns = GroundedAnswerService.historyMessages(List.of(
                new ConversationService.Exchange("Cây nhị phân là gì?", "Mỗi nút có tối đa hai con [1]. Xem thêm [2].")));

        assertThat(turns).extracting(ChatRequest.Message::role)
                .containsExactly(ChatRequest.Role.USER, ChatRequest.Role.ASSISTANT);
        assertThat(turns.get(1).text()).isEqualTo("Mỗi nút có tối đa hai con. Xem thêm.");
    }

    @Test
    @DisplayName("Ghim giữ thời điểm ghim đầu tiên; bỏ vào thùng rác thì bỏ ghim; tên mới được gọn như tên tự đặt")
    void pinRenameTrash() {
        Instant now = Instant.parse("2026-11-11T03:00:00Z");
        Conversation conversation = Conversation.start(UUID.randomUUID(), null, "Câu hỏi đầu", now);

        conversation.pin(true, now);
        conversation.pin(true, now.plusSeconds(60));
        assertThat(conversation.getPinnedAt()).isEqualTo(now);

        conversation.rename("  Ôn   tập  ");
        assertThat(conversation.getTitle()).isEqualTo("Ôn tập");
        conversation.rename("  ");
        assertThat(conversation.getTitle()).isEqualTo("Ôn tập");

        conversation.trash(now);
        assertThat(conversation.isPinned()).isFalse();
        assertThat(conversation.isTrashed()).isTrue();
    }
}
