package com.learningcompanion.library.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Tạo thư mục mới trong thư mục cha ({@code parentId} rỗng: thư mục gốc). */
public record FolderRequest(@NotBlank @Size(max = 120) String name, UUID parentId) {
}
