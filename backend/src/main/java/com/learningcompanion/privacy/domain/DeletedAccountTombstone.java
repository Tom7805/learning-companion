package com.learningcompanion.privacy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Dấu vết một tài khoản đã bị xóa vĩnh viễn: không có địa chỉ thư, tên hay mã tài khoản cũ, chỉ có mã ẩn danh
 * (cũng là mã thay cho tài khoản trong nhật ký), các mốc thời gian và số mục đã xóa của từng phần dữ liệu.
 */
@Entity
@Immutable
@Table(name = "deleted_account_tombstones")
public class DeletedAccountTombstone {

    @Id
    private UUID id;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "deleted_at", nullable = false)
    private Instant deletedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Integer> erased = new LinkedHashMap<>();

    protected DeletedAccountTombstone() {
    }

    public static DeletedAccountTombstone of(UUID anonymousId, Instant requestedAt, Instant deletedAt,
                                             Map<String, Integer> erased) {
        DeletedAccountTombstone tombstone = new DeletedAccountTombstone();
        tombstone.id = anonymousId;
        tombstone.requestedAt = requestedAt;
        tombstone.deletedAt = deletedAt;
        tombstone.erased = new LinkedHashMap<>(erased);
        return tombstone;
    }

    public UUID getId() {
        return id;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Map<String, Integer> getErased() {
        return erased;
    }
}
