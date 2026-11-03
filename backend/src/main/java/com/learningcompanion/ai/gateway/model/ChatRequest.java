package com.learningcompanion.ai.gateway.model;

import java.util.List;
import java.util.Objects;

/**
 * Yêu cầu sinh văn bản: lời nhắc hệ thống và các lượt hội thoại. Nội dung chỉ đi tới nhà cung cấp, không bao giờ được
 * ghi vào sổ đo mức sử dụng hay nhật ký.
 */
public record ChatRequest(String system, List<Message> messages, int maxOutputUnits) {

    public enum Role { USER, ASSISTANT }

    public record Message(Role role, String text) {

        public Message {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(text, "text");
        }
    }

    public ChatRequest {
        messages = List.copyOf(messages);
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Cần ít nhất một lượt hội thoại");
        }
        if (maxOutputUnits <= 0) {
            maxOutputUnits = 1024;
        }
    }

    public static ChatRequest of(String system, String userText) {
        return new ChatRequest(system, List.of(new Message(Role.USER, userText)), 1024);
    }
}
