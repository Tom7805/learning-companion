package com.learningcompanion.library.api.dto;

import com.learningcompanion.library.application.DocumentService;
import com.learningcompanion.library.application.PastedTextService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Tạo tài liệu văn bản từ đoạn dán vào kèm tiêu đề. {@code folderId} trống: thư mục gốc. */
public record PasteTextRequest(
        @NotBlank @Size(max = DocumentService.TITLE_MAX_LENGTH) String title,
        @NotBlank @Size(max = PastedTextService.TEXT_MAX_LENGTH) String text,
        UUID folderId) {
}
