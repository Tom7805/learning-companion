package com.learningcompanion.ai.safety.domain;

/**
 * Nhóm nội dung không an toàn mà bộ lọc kiểm (NCL-04-CN-004, QTN-15). Năm nhóm đầu có ngưỡng lọc do quản trị viên
 * đặt; nhóm tự gây hại không bị chặn cụt mà luôn nhận câu trả lời ân cần kèm nguồn trợ giúp, nên không có ngưỡng.
 */
public enum ModerationCategory {
    VIOLENCE,
    HATE,
    SEXUAL,
    /** Hướng dẫn gây hại: chế tạo vũ khí, chất nổ, chất độc, ma túy. */
    HARMFUL_INSTRUCTIONS,
    /** Lừa đảo, chiếm đoạt tài khoản, làm giả giấy tờ. */
    FRAUD,
    SELF_HARM;

    /** Nhóm có ngưỡng lọc chỉnh được. */
    public boolean configurable() {
        return this != SELF_HARM;
    }
}
