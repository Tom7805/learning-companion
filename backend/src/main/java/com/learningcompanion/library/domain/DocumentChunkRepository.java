package com.learningcompanion.library.domain;

import com.learningcompanion.shared.ownership.OwnerScopedRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Đoạn của tài liệu; mọi truy vấn đều kèm chủ sở hữu, xem {@link OwnerScopedRepository}. */
public interface DocumentChunkRepository extends OwnerScopedRepository<DocumentChunk> {

    List<DocumentChunk> findAllByOwnerIdAndDocumentIdOrderByOrdinalAsc(UUID ownerId, UUID documentId);

    long countByOwnerIdAndDocumentId(UUID ownerId, UUID documentId);

    /** Đoạn của các tài liệu đã sẵn sàng và còn trong kho, trong phạm vi truy xuất của hỏi đáp (NCL-07-CN-001). */
    @Query("select c from DocumentChunk c, Document d where d.id = c.documentId and c.ownerId = :ownerId "
            + "and d.ownerId = :ownerId and c.documentId in :documentIds and d.status = :ready "
            + "and d.trashedAt is null order by c.documentId, c.ordinal")
    List<DocumentChunk> findReadyByOwnerIdAndDocumentIdIn(@Param("ownerId") UUID ownerId,
                                                          @Param("documentIds") java.util.Collection<UUID> documentIds,
                                                          @Param("ready") DocumentStatus ready);

    /** Đoạn của mọi tài liệu đã sẵn sàng và còn trong kho của người học. */
    @Query("select c from DocumentChunk c, Document d where d.id = c.documentId and c.ownerId = :ownerId "
            + "and d.ownerId = :ownerId and d.status = :ready and d.trashedAt is null order by c.documentId, c.ordinal")
    List<DocumentChunk> findReadyByOwnerId(@Param("ownerId") UUID ownerId, @Param("ready") DocumentStatus ready);

    /** Bỏ các đoạn cũ trước khi lưu kết quả của lần xử lý mới. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DocumentChunk c where c.ownerId = :ownerId and c.documentId = :documentId")
    int deleteByOwnerIdAndDocumentId(@Param("ownerId") UUID ownerId, @Param("documentId") UUID documentId);
}
