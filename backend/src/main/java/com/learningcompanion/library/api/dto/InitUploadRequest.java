package com.learningcompanion.library.api.dto;

import com.learningcompanion.library.application.UploadService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Bắt đầu tải một tệp lên kho. {@code folderId} trống: thư mục gốc. {@code sha256}: dấu vân tay giao diện tự tính
 * (không bắt buộc), để báo trùng ngay mà không phải gửi cả tệp; máy chủ vẫn tính lại khi hoàn tất.
 */
public record InitUploadRequest(
        @NotBlank @Size(max = UploadService.FILE_NAME_MAX_LENGTH) String fileName,
        @NotNull @Positive Long sizeBytes,
        UUID folderId,
        @Pattern(regexp = "[0-9a-fA-F]{64}") String sha256) {
}
