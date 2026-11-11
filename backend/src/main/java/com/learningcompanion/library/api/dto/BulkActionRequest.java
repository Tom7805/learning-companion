package com.learningcompanion.library.api.dto;

import com.learningcompanion.library.application.BulkActionService;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Thao tác trên nhiều tài liệu: {@code folderId} cho MOVE (rỗng là thư mục gốc), {@code tagIds} cho ADD_TAGS và
 * REMOVE_TAGS, {@code confirmInUse} cho TRASH khi có tài liệu đang gắn với không gian học.
 */
public record BulkActionRequest(@NotNull BulkActionService.Action action, @NotEmpty List<UUID> documentIds,
                                UUID folderId, List<UUID> tagIds, Boolean confirmInUse) {
}
