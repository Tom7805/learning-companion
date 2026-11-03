package com.learningcompanion.ai.quota.domain;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Các câu lệnh đổi sổ lượt đều có điều kiện và chạy nguyên khối trong cơ sở dữ liệu, nên hai tác vụ bắt đầu cùng lúc
 * không thể cùng lọt qua khi chỉ còn đủ lượt cho một tác vụ.
 */
public interface UsageLedgerRepository extends JpaRepository<UsageLedgerEntry, UsageLedgerEntry.Key> {

    @Modifying
    @Query(value = """
            insert into quota_daily_usage (owner_id, study_day, used_units, reserved_units)
            values (:ownerId, :day, 0, 0) on conflict do nothing""", nativeQuery = true)
    void ensureRow(@Param("ownerId") UUID ownerId, @Param("day") LocalDate day);

    /** Giữ chỗ nếu còn đủ lượt; trả về 0 khi không đủ. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update quota_daily_usage set reserved_units = reserved_units + :units
            where owner_id = :ownerId and study_day = :day and used_units + reserved_units + :units <= :limit""",
            nativeQuery = true)
    int reserve(@Param("ownerId") UUID ownerId, @Param("day") LocalDate day, @Param("units") int units,
                @Param("limit") int limit);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update quota_daily_usage set reserved_units = greatest(reserved_units - :units, 0),
                used_units = used_units + :units
            where owner_id = :ownerId and study_day = :day""", nativeQuery = true)
    int commit(@Param("ownerId") UUID ownerId, @Param("day") LocalDate day, @Param("units") int units);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update quota_daily_usage set reserved_units = greatest(reserved_units - :units, 0)
            where owner_id = :ownerId and study_day = :day""", nativeQuery = true)
    int release(@Param("ownerId") UUID ownerId, @Param("day") LocalDate day, @Param("units") int units);
}
