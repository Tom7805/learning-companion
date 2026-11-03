package com.learningcompanion.ai.safety.domain;

/**
 * Ngưỡng lọc của một nhóm nội dung. Mỗi dấu hiệu trong bộ lọc có mức độ từ 1 (nhẹ, ví dụ chỉ nhắc tới) đến 3 (yêu cầu
 * trắng trợn); nội dung bị chặn khi có dấu hiệu từ mức tối thiểu của ngưỡng trở lên. Không có ngưỡng "tắt": quản trị
 * viên chỉ nới được tới mức chặn yêu cầu trắng trợn.
 */
public enum SafetyLevel {
    STRICT(1),
    STANDARD(2),
    LENIENT(3);

    private final int minimumSeverity;

    SafetyLevel(int minimumSeverity) {
        this.minimumSeverity = minimumSeverity;
    }

    public boolean blocks(int severity) {
        return severity >= minimumSeverity;
    }
}
