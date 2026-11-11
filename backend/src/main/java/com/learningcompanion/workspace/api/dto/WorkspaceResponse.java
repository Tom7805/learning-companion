package com.learningcompanion.workspace.api.dto;

import com.learningcompanion.workspace.application.WorkspaceService;
import com.learningcompanion.workspace.domain.Workspace;
import java.time.Instant;
import java.util.UUID;

/** Một không gian học kèm số tài liệu còn trong kho đang gắn với nó. */
public record WorkspaceResponse(UUID id, String name, String icon, String color, String description,
                                long documentCount, Instant createdAt, Instant updatedAt, Instant lastOpenedAt) {

    public static WorkspaceResponse of(WorkspaceService.Overview overview) {
        Workspace workspace = overview.workspace();
        return new WorkspaceResponse(workspace.getId(), workspace.getName(), workspace.getIcon().name(),
                workspace.getColor().name(), workspace.getDescription(), overview.documentCount(),
                workspace.getCreatedAt(), workspace.getUpdatedAt(), workspace.getLastOpenedAt());
    }

    /** Tên và hình của một không gian học, cho danh sách "đang dùng trong" của tài liệu. */
    public record Summary(UUID id, String name, String icon, String color) {

        public static Summary of(Workspace workspace) {
            return new Summary(workspace.getId(), workspace.getName(), workspace.getIcon().name(),
                    workspace.getColor().name());
        }
    }
}
