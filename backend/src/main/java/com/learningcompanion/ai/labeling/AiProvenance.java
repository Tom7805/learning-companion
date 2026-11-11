package com.learningcompanion.ai.labeling;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Nguồn gốc của một nội dung do trí tuệ nhân tạo sinh ra (NCL-04-CN-005, QTN-23): chức năng đã sinh, lúc sinh và lúc
 * người học sửa tay gần nhất. Nội dung đã sửa vẫn giữ nhãn, chỉ ghi thêm "đã chỉnh sửa".
 *
 * @param contentId mã của nội dung (câu trả lời, học liệu) để đối chiếu khi cần
 * @param feature   mã chức năng trí tuệ nhân tạo đã sinh nội dung, ví dụ {@code ASSISTANT_CHAT}
 * @param editedAt  lúc sửa tay gần nhất; {@code null} khi chưa sửa
 */
public record AiProvenance(UUID contentId, String feature, Instant generatedAt, Instant editedAt) {

    /** Tên hệ thống ghi trong dấu nhận biết. */
    public static final String GENERATOR = "Learning Companion";

    public AiProvenance {
        Objects.requireNonNull(contentId, "contentId");
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(generatedAt, "generatedAt");
    }

    public static AiProvenance generated(UUID contentId, String feature, Instant generatedAt) {
        return new AiProvenance(contentId, feature, generatedAt, null);
    }

    public boolean edited() {
        return editedAt != null;
    }
}
