package com.learningcompanion.library.domain;

import com.learningcompanion.shared.ownership.OwnerScopedRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Thư mục của kho tài liệu, luôn tra kèm chủ sở hữu; xem {@link OwnerScopedRepository}. */
public interface FolderRepository extends OwnerScopedRepository<Folder> {

    List<Folder> findAllByOwnerIdAndTrashedAtIsNullOrderByNameAsc(UUID ownerId);

    List<Folder> findAllByOwnerIdAndParentIdAndTrashedAtIsNull(UUID ownerId, UUID parentId);

    List<Folder> findAllByOwnerIdAndParentIdIsNullAndTrashedAtIsNull(UUID ownerId);

    /** Đưa cả nhóm thư mục vào thùng rác cùng một mã đợt xóa. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Folder f set f.trashedAt = :now, f.trashBatchId = :batch, f.updatedAt = :now "
            + "where f.ownerId = :ownerId and f.id in :ids and f.trashedAt is null")
    int trashByOwnerIdAndIdIn(@Param("ownerId") UUID ownerId, @Param("ids") Collection<UUID> ids,
                              @Param("batch") UUID batch, @Param("now") Instant now);
}
