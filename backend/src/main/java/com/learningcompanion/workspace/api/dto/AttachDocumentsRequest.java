package com.learningcompanion.workspace.api.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/** Các tài liệu của kho cần gắn vào không gian học. */
public record AttachDocumentsRequest(@NotEmpty List<UUID> documentIds) {
}
