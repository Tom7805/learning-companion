package com.learningcompanion.ai.quota.domain;

/**
 * Các loại tác vụ trí tuệ nhân tạo tốn lượt (NCL-04-CN-003). Tác vụ nhẹ tốn một lượt, tác vụ sinh nội dung dài tốn
 * nhiều lượt hơn; số lượt thật do quản trị viên chỉnh trong bảng quy đổi, giá trị ở đây chỉ là mặc định khi loại tác
 * vụ được đăng ký lần đầu. Chức năng mới tốn lượt thì thêm một loại ở đây và bản dịch tên ở giao diện.
 *
 * <p>Mọi thao tác chỉ đọc (xem tài liệu, ôn thẻ đã có, làm bài kiểm tra đã sinh...) không tốn lượt và không bao giờ
 * đi qua sổ lượt (QTN-13, QTN-14).
 */
public enum QuotaTask {

    /** Hỏi đáp một câu trên tài liệu. */
    ASSISTANT_QUESTION(Weight.LIGHT, 1),
    /** Giải thích một đoạn đang đọc. */
    EXPLAIN_PASSAGE(Weight.LIGHT, 1),
    /** Đọc chữ trong ảnh hay bản chụp. */
    IMAGE_TEXT_RECOGNITION(Weight.LIGHT, 1),
    /** Tóm tắt cả tài liệu. */
    DOCUMENT_SUMMARY(Weight.LONG, 3),
    /** Sinh bộ thẻ ghi nhớ từ tài liệu. */
    FLASHCARD_GENERATION(Weight.LONG, 3),
    /** Sinh bài kiểm tra từ tài liệu. */
    QUIZ_GENERATION(Weight.LONG, 5),
    /** Đề xuất dàn ý chủ đề của không gian học từ các nguồn (NCL-06-CN-002). */
    TOPIC_OUTLINE(Weight.LONG, 2),
    /** Sinh đề cương ôn tập dài. */
    STUDY_GUIDE(Weight.LONG, 5);

    /** Tác vụ nhẹ (một câu trả lời ngắn) hay sinh nội dung dài. */
    public enum Weight { LIGHT, LONG }

    private final Weight weight;
    private final int defaultUnits;

    QuotaTask(Weight weight, int defaultUnits) {
        this.weight = weight;
        this.defaultUnits = defaultUnits;
    }

    public Weight weight() {
        return weight;
    }

    public int defaultUnits() {
        return defaultUnits;
    }
}
