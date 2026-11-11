package com.learningcompanion.workspace.api.dto;

import com.learningcompanion.library.domain.TagColor;
import com.learningcompanion.workspace.domain.Workspace;
import com.learningcompanion.workspace.domain.WorkspaceIcon;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Tạo hoặc sửa không gian học: tên bắt buộc tối đa một trăm ký tự; biểu tượng, màu và mô tả tùy chọn. */
public record WorkspaceRequest(@NotBlank @Size(max = Workspace.NAME_MAX_LENGTH) String name, WorkspaceIcon icon,
                               TagColor color,
                               @Size(max = Workspace.DESCRIPTION_MAX_LENGTH) String description) {
}
