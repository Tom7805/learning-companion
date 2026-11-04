package com.learningcompanion.library.api.dto;

import com.learningcompanion.library.application.FolderService;
import com.learningcompanion.library.domain.Folder;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cây thư mục dạng danh sách phẳng (giao diện tự dựng cây theo {@code parentId}) kèm số tài liệu nằm trực tiếp trong
 * từng thư mục, số tài liệu ở thư mục gốc, tổng số tài liệu và số tài liệu có gắn sao.
 */
public record FolderTreeResponse(List<FolderResponse> folders, long rootDocumentCount, long totalDocumentCount,
                                 long starredCount) {

    public static FolderTreeResponse of(FolderService.Tree tree) {
        return new FolderTreeResponse(
                tree.folders().stream().map(node -> FolderResponse.of(node.folder(), node.documentCount())).toList(),
                tree.rootDocumentCount(), tree.totalDocumentCount(), tree.starredCount());
    }

    public record FolderResponse(UUID id, UUID parentId, String name, long documentCount, Instant createdAt) {

        public static FolderResponse of(Folder folder, long documentCount) {
            return new FolderResponse(folder.getId(), folder.getParentId(), folder.getName(), documentCount,
                    folder.getCreatedAt());
        }
    }
}
