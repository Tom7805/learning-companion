package com.learningcompanion.ai.safety.application;

import com.learningcompanion.ai.safety.domain.ModerationCategory;

/**
 * Kết luận của bộ lọc cho một đoạn văn bản.
 *
 * @param verdict  cho qua, chặn, hay (chỉ với câu hỏi) chuyển sang câu trả lời ân cần kèm nguồn trợ giúp
 * @param category nhóm vi phạm; trống khi cho qua
 * @param severity mức độ dấu hiệu mạnh nhất tìm thấy, 0 khi cho qua
 */
public record ModerationResult(Verdict verdict, ModerationCategory category, int severity) {

    public enum Verdict { ALLOW, BLOCK, SUPPORT }

    public static final ModerationResult ALLOWED = new ModerationResult(Verdict.ALLOW, null, 0);

    public boolean allowed() {
        return verdict == Verdict.ALLOW;
    }
}
