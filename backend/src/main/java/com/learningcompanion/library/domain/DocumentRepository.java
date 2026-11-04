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

    /** Tài liệu còn hoạt động có cùng dấu vân tay nội dung, ở bất kỳ thư mục nào của kho (QTN-17). */
    List<Document> findAllByOwnerIdAndContentSha256AndTrashedAtIsNullOrderByCreatedAtAsc(UUID ownerId,
                                                                                        String contentSha256);

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

    /**
     * Nhận một tài liệu để xử lý (NCL-05-CN-003): chỉ tài liệu chờ xử lý, hoặc đang xử lý nhưng bắt đầu trước
     * {@code staleBefore} (máy chủ dừng giữa chừng). Trả về 1 nếu nhận được; hai máy chủ không bao giờ cùng nhận một
     * tài liệu, nên mỗi tài liệu chỉ được xử lý một lần.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Document d set d.status = :processing, d.processingStartedAt = :now, "
            + "d.processingAttempts = d.processingAttempts + 1, d.version = d.version + 1 "
            + "where d.id = :id and d.ownerId = :ownerId and d.trashedAt is null and (d.status = :pending "
            + "or (d.status = :processing and d.processingStartedAt < :staleBefore))")
    int claimForProcessingByIdAndOwnerId(@Param("id") UUID id, @Param("ownerId") UUID ownerId,
                                         @Param("now") Instant now, @Param("staleBefore") Instant staleBefore,
                                         @Param("pending") DocumentStatus pending,
                                         @Param("processing") DocumentStatus processing);
}
