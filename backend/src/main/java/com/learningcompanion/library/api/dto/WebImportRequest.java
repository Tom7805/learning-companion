package com.learningcompanion.library.api.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Thêm tài liệu từ đường dẫn trang web. {@code folderId} trống: thư mục gốc. Đường dẫn được kiểm kỹ ở máy chủ (giao thức,
 * mạng nội bộ) để trả lời lỗi dễ hiểu thay vì lỗi kiểm tra dữ liệu chung.
 */
public record WebImportRequest(@Size(max = 8192) String url, UUID folderId) {
}
