package com.learningcompanion.notification.domain;

import com.learningcompanion.shared.ownership.OwnerScopedRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends OwnerScopedRepository<Notification> {

    /** Trang đầu: mới nhất trước. */
    List<Notification> findByOwnerIdOrderByCreatedAtDescIdDesc(UUID ownerId, Pageable page);

    /** Trang kế tiếp: những thông báo cũ hơn mốc (thời điểm, mã) của mục cuối trang trước. */
    @Query("""
            select n from Notification n
            where n.ownerId = :ownerId
              and (n.createdAt < :createdAt or (n.createdAt = :createdAt and n.id < :id))
            order by n.createdAt desc, n.id desc""")
    List<Notification> findOlderByOwnerId(@Param("ownerId") UUID ownerId, @Param("createdAt") Instant createdAt,
                                          @Param("id") UUID id, Pageable page);

    long countByOwnerIdAndReadAtIsNull(UUID ownerId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.ownerId = :ownerId and n.readAt is null")
    int markAllReadByOwnerId(@Param("ownerId") UUID ownerId, @Param("now") Instant now);

    List<Notification> findAllByOwnerIdOrderByCreatedAtAsc(UUID ownerId);
}
