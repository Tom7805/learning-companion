package com.learningcompanion.admin.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Những gì quản trị viên được thấy về một tài khoản (QTN-07): siêu dữ liệu vận hành, số bản ghi
 * học tập và dung lượng đã dùng. Kiểu dữ liệu này cố ý không có trường nội dung học tập nào,
 * nên dù gọi kèm tham số gì cũng không thể trả nội dung.
 */
public record AdminUserSummary(UUID id, String email, String displayName, String role, String status,
                               Instant createdAt, Instant emailVerifiedAt, long learningRecords,
                               long storageBytesUsed) {
}
