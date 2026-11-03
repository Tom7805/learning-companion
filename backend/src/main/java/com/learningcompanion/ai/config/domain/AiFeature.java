package com.learningcompanion.ai.config.domain;

import com.learningcompanion.ai.gateway.AiCapability;
import java.math.BigDecimal;

/**
 * Các chức năng trí tuệ nhân tạo có cấu hình riêng theo phiên bản (NCL-04-CN-002). Chức năng mới đăng ký ở đây ngay khi
 * được xây dựng, kèm giá trị ban đầu; lần khởi động đầu tiên hệ thống tạo phiên bản 1 từ các giá trị này và phát hành
 * luôn. Từ đó mọi thay đổi đi qua trang quản trị và có lịch sử. Mã chức năng cũng là mã ghi trong sổ đo mức sử dụng.
 */
public enum AiFeature {

    /** Trợ lý hỏi đáp có căn cứ trên tài liệu của người học. */
    ASSISTANT_CHAT(AiCapability.TEXT_GENERATION, "0.30", 1024, """
            Bạn là trợ lý học tập của Learning Companion. Chỉ trả lời dựa trên các đoạn nguồn được cung cấp trong \
            câu hỏi. Mỗi ý lấy từ nguồn phải ghi số nguồn trong ngoặc vuông, ví dụ [1]. Nếu nguồn không đủ để trả \
            lời, hãy nói rõ là tài liệu chưa có thông tin đó thay vì tự suy đoán.
            Trả lời bằng ngôn ngữ của câu hỏi, ngắn gọn, dễ hiểu với học sinh và sinh viên."""),

    /** Lời gọi thử trên trang lớp kết nối, để quản trị viên kiểm tra nhà cung cấp còn trả lời. */
    ADMIN_TEST(AiCapability.TEXT_GENERATION, "0.20", 512,
            "Bạn là trợ lý học tập. Trả lời ngắn gọn bằng tiếng Việt.");

    private final AiCapability capability;
    private final BigDecimal defaultTemperature;
    private final int defaultMaxOutputTokens;
    private final String defaultSystemPrompt;

    AiFeature(AiCapability capability, String defaultTemperature, int defaultMaxOutputTokens,
              String defaultSystemPrompt) {
        this.capability = capability;
        this.defaultTemperature = new BigDecimal(defaultTemperature);
        this.defaultMaxOutputTokens = defaultMaxOutputTokens;
        this.defaultSystemPrompt = defaultSystemPrompt;
    }

    public AiCapability capability() {
        return capability;
    }

    public BigDecimal defaultTemperature() {
        return defaultTemperature;
    }

    public int defaultMaxOutputTokens() {
        return defaultMaxOutputTokens;
    }

    public String defaultSystemPrompt() {
        return defaultSystemPrompt;
    }
}
