package com.learningcompanion.ai.safety.application;

import com.learningcompanion.shared.audit.AuditObjectType;
import java.util.UUID;

/**
 * Một đoạn nguồn sắp ghép vào ngữ cảnh của mô hình (NCL-04-CN-004). Kiểu và mã đối tượng (ví dụ đoạn của một tài
 * liệu) được ghi vào nhật ký khi đoạn bị gắn cờ; để trống khi nguồn không thuộc đối tượng nào, ví dụ nguồn mẫu.
 */
public record SourcePassage(String title, String text, AuditObjectType objectType, UUID objectId) {

    public static SourcePassage of(String title, String text) {
        return new SourcePassage(title, text, null, null);
    }
}
