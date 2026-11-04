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

    /** Bỏ các đoạn cũ trước khi lưu kết quả của lần xử lý mới. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DocumentChunk c where c.ownerId = :ownerId and c.documentId = :documentId")
    int deleteByOwnerIdAndDocumentId(@Param("ownerId") UUID ownerId, @Param("documentId") UUID documentId);
}
