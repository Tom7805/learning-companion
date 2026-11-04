package com.learningcompanion.library.domain;

import com.learningcompanion.shared.ownership.OwnerScopedRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Mọi truy vấn tài liệu đều kèm chủ sở hữu; xem {@link OwnerScopedRepository}. */
public interface DocumentRepository extends OwnerScopedRepository<Document> {

    List<Document> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    @Query("select coalesce(sum(d.sizeBytes), 0) from Document d where d.ownerId = :ownerId")
    long sumSizeBytesByOwnerId(@Param("ownerId") UUID ownerId);

    List<Document> findAllByOwnerIdAndFolderIdAndTrashedAtIsNull(UUID ownerId, UUID folderId);

    List<Document> findAllByOwnerIdAndFolderIdIsNullAndTrashedAtIsNull(UUID ownerId);

    long countByOwnerIdAndTrashedAtIsNull(UUID ownerId);

    long countByOwnerIdAndStarredTrueAndTrashedAtIsNull(UUID ownerId);

    /** Số tài liệu nằm trực tiếp trong từng thư mục (thư mục rỗng: thư mục gốc), không tính thùng rác. */
    @Query("select d.folderId, count(d) from Document d where d.ownerId = :ownerId and d.trashedAt is null "
            + "group by d.folderId")
    List<Object[]> countActiveByOwnerIdGroupedByFolder(@Param("ownerId") UUID ownerId);

    /** Số tài liệu đang gắn từng nhãn, không tính thùng rác. */
    @Query("select t, count(d) from Document d join d.tagIds t where d.ownerId = :ownerId and d.trashedAt is null "
            + "group by t")
    List<Object[]> countActiveByOwnerIdGroupedByTag(@Param("ownerId") UUID ownerId);

    /** Đưa mọi tài liệu còn hoạt động trong các thư mục này vào thùng rác cùng đợt xóa của thư mục. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Document d set d.trashedAt = :now, d.trashBatchId = :batch "
            + "where d.ownerId = :ownerId and d.folderId in :folderIds and d.trashedAt is null")
    int trashByOwnerIdAndFolderIdIn(@Param("ownerId") UUID ownerId, @Param("folderIds") Collection<UUID> folderIds,
                                    @Param("batch") UUID batch, @Param("now") Instant now);
}
