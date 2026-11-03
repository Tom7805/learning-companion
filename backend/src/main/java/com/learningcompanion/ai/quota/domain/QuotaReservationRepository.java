package com.learningcompanion.ai.quota.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuotaReservationRepository extends JpaRepository<QuotaReservation, UUID> {

    /** Chốt trừ lượt; trả về 0 nếu lần giữ chỗ đã được chốt hay hoàn lại trước đó. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update QuotaReservation r set r.status = 'COMMITTED', r.settledAt = :now
            where r.id = :id and r.status = 'RESERVED'""")
    int markCommitted(@Param("id") UUID id, @Param("now") Instant now);

    /** Hoàn lại lượt; trả về 0 nếu lần giữ chỗ đã được chốt hay hoàn lại trước đó. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update QuotaReservation r set r.status = 'RELEASED', r.releaseReason = :reason, r.settledAt = :now
            where r.id = :id and r.status = 'RESERVED'""")
    int markReleased(@Param("id") UUID id, @Param("reason") QuotaReservation.ReleaseReason reason,
                     @Param("now") Instant now);

    /** Lần giữ chỗ treo quá lâu, cũ nhất trước. */
    @Query("select r.id from QuotaReservation r where r.status = 'RESERVED' and r.createdAt < :before "
            + "order by r.createdAt")
    List<UUID> findStaleIds(@Param("before") Instant before, Limit limit);
}
