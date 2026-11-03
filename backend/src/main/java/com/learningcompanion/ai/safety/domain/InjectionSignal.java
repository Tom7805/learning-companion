package com.learningcompanion.ai.safety.domain;

/** Kiểu câu lệnh tìm thấy trong đoạn nguồn (NCL-04-CN-004, QTN-16). */
public enum InjectionSignal {
    /** Yêu cầu bỏ qua, quên hay ghi đè chỉ dẫn trước đó. */
    IGNORE_INSTRUCTIONS,
    /** Yêu cầu tiết lộ hay nhắc lại lời nhắc hệ thống. */
    REVEAL_SYSTEM_PROMPT,
    /** Ép trợ lý đổi vai, đổi quy tắc từ giờ trở đi. */
    ROLE_OVERRIDE,
    /** Giả dấu ranh giới vùng dữ liệu hay khối chỉ dẫn hệ thống. */
    FAKE_DELIMITER
}
