package com.learningcompanion.library.domain;

import com.learningcompanion.shared.ownership.OwnerScopedRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Mọi truy vấn tài liệu đều kèm chủ sở hữu; xem {@link OwnerScopedRepository}. */
public interface DocumentRepository extends OwnerScopedRepository<Document> {

    List<Document> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    @Query("select coalesce(sum(d.sizeBytes), 0) from Document d where d.ownerId = :ownerId")
    long sumSizeBytesByOwnerId(@Param("ownerId") UUID ownerId);
}
