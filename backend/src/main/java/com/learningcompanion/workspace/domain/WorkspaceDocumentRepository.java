package com.learningcompanion.workspace.domain;

import com.learningcompanion.shared.ownership.OwnerScopedRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Liên kết tài liệu với không gian học, luôn tra kèm chủ sở hữu. Tài liệu hay không gian học đã vào thùng rác vẫn giữ
 * liên kết (để khôi phục được nguyên trạng) nhưng không bao giờ được tính hay liệt kê.
 */
public interface WorkspaceDocumentRepository extends OwnerScopedRepository<WorkspaceDocument> {

    Optional<WorkspaceDocument> findByOwnerIdAndWorkspaceIdAndDocumentId(UUID ownerId, UUID workspaceId,
                                                                         UUID documentId);

    List<WorkspaceDocument> findAllByOwnerIdAndWorkspaceId(UUID ownerId, UUID workspaceId);

    List<WorkspaceDocument> findAllByOwnerIdOrderByAttachedAtAsc(UUID ownerId);

    /** Số tài liệu còn trong kho đang gắn với từng không gian học. */
    @Query("select link.workspaceId, count(link) from WorkspaceDocument link, Document d "
            + "where d.id = link.documentId and link.ownerId = :ownerId and d.ownerId = :ownerId "
            + "and d.trashedAt is null group by link.workspaceId")
    List<Object[]> countActiveByOwnerIdGroupedByWorkspace(@Param("ownerId") UUID ownerId);

    /** Tài liệu còn trong kho của một không gian học kèm thời điểm gắn, mới gắn đứng trước. */
    @Query("select d, link.attachedAt from WorkspaceDocument link, Document d "
            + "where d.id = link.documentId and link.ownerId = :ownerId and d.ownerId = :ownerId "
            + "and link.workspaceId = :workspaceId and d.trashedAt is null "
            + "order by link.attachedAt desc, d.title asc")
    List<Object[]> findActiveDocumentsByOwnerIdAndWorkspaceId(@Param("ownerId") UUID ownerId,
                                                              @Param("workspaceId") UUID workspaceId);

    /** Cặp (mã tài liệu, không gian học đang dùng nó); không gian học trong thùng rác không tính. */
    @Query("select link.documentId, w from WorkspaceDocument link, Workspace w "
            + "where w.id = link.workspaceId and link.ownerId = :ownerId and w.ownerId = :ownerId "
            + "and link.documentId in :documentIds and w.trashedAt is null order by w.name asc")
    List<Object[]> findActiveWorkspacesByOwnerIdAndDocumentIdIn(@Param("ownerId") UUID ownerId,
                                                                @Param("documentIds") Collection<UUID> documentIds);
}
