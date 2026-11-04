package com.learningcompanion.library.api.dto;

import com.learningcompanion.library.domain.TagColor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Tạo hoặc sửa nhãn; màu rỗng khi tạo là màu vàng. */
public record TagRequest(@NotBlank @Size(max = 40) String name, TagColor color) {
}
