package com.learningcompanion.ai.gateway;

/**
 * Khả năng mà lớp kết nối cung cấp (NCL-04-CN-001). Mỗi khả năng có định tuyến riêng: nhà cung cấp chính, dự phòng,
 * thời gian chờ, số lần thử lại và ngưỡng ngắt mạch. Chức năng mới cần khả năng khác thì thêm một giá trị ở đây, một
 * phương thức trong {@link AiGateway} và cách gọi trong các bộ chuyển đổi nhà cung cấp.
 */
public enum AiCapability {
    /** Sinh văn bản theo luồng. */
    TEXT_GENERATION,
    /** Tạo biểu diễn ngữ nghĩa (vector) cho tìm kiếm theo nghĩa. */
    EMBEDDING,
    /** Đọc và mô tả hình ảnh. */
    VISION,
    /** Nhận dạng chữ trong hình ảnh. */
    OCR
}
