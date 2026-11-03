package com.learningcompanion.ai.gateway.model;

import java.util.List;
import java.util.Objects;

/**
 * Yêu cầu sinh văn bản: lời nhắc hệ thống và các lượt hội thoại. Nội dung chỉ đi tới nhà cung cấp, không bao giờ được
 * ghi vào sổ đo mức sử dụng hay nhật ký.
 *
 * <p>{@link Tuning} mang phần cấu hình theo phiên bản của chức năng (NCL-04-CN-002): nhà cung cấp và mô hình ưu tiên,
 * nhiệt độ và số phiên bản cấu hình đã dùng. Để trống thì lớp kết nối đi theo định tuyến của khả năng.
 */
public record ChatRequest(String system, List<Message> messages, int maxOutputUnits, Tuning tuning) {

    public enum Role { USER, ASSISTANT }

    public record Message(Role role, String text) {

        public Message {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * Tham số lấy từ phiên bản cấu hình đang phát hành của chức năng.
     *
     * @param providerCode  nhà cung cấp ưu tiên; null thì dùng nhà cung cấp chính của định tuyến
     * @param model         mô hình trên nhà cung cấp ưu tiên; null thì dùng mô hình mặc định của nhà cung cấp
     * @param temperature   nhiệt độ lấy mẫu; null thì để nhà cung cấp tự chọn
     * @param configVersion số phiên bản cấu hình, được ghi vào sổ đo để biết lời gọi chạy theo phiên bản nào
     */
    public record Tuning(String providerCode, String model, Double temperature, Integer configVersion) {

        public static final Tuning NONE = new Tuning(null, null, null, null);
    }

    public ChatRequest {
        messages = List.copyOf(messages);
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Cần ít nhất một lượt hội thoại");
        }
        if (maxOutputUnits <= 0) {
            maxOutputUnits = 1024;
        }
        if (tuning == null) {
            tuning = Tuning.NONE;
        }
    }

    public ChatRequest(String system, List<Message> messages, int maxOutputUnits) {
        this(system, messages, maxOutputUnits, Tuning.NONE);
    }

    public static ChatRequest of(String system, String userText) {
        return new ChatRequest(system, List.of(new Message(Role.USER, userText)), 1024);
    }
}
