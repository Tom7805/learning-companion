package com.learningcompanion.ai.quota.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Một lần chạy tác vụ tốn lượt (NCL-04-CN-003): lượt được giữ chỗ khi tác vụ bắt đầu, trừ khi hoàn tất và hoàn lại khi
 * lỗi hoặc bị hủy. Ngày học được chốt lúc giữ chỗ, nên tác vụ chạy vắt qua nửa đêm vẫn trừ vào đúng ngày đã giữ.
 * Trạng thái chỉ đổi qua câu lệnh có điều kiện trong {@link QuotaReservationRepository}, để mỗi lần giữ chỗ chỉ được
 * chốt đúng một lần.
 */
@Entity
@Table(name = "quota_reservations")
public class QuotaReservation {

    public enum Status { RESERVED, COMMITTED, RELEASED }

    /** Vì sao lượt được hoàn lại. */
    public enum ReleaseReason {
        /** Dịch vụ bên ngoài lỗi; người học được báo là không bị trừ lượt. */
        FAILED,
        /** Người học hủy tác vụ. */
        CANCELLED,
        /** Tác vụ treo quá lâu mà không chốt; hệ thống tự hoàn lại. */
        EXPIRED
    }

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private QuotaTask task;

    @Column(nullable = false)
    private int units;

    @Column(name = "study_day", nullable = false)
    private LocalDate studyDay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(name = "release_reason", length = 20)
    private ReleaseReason releaseReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    protected QuotaReservation() {
    }

    public static QuotaReservation reserve(UUID ownerId, QuotaTask task, int units, LocalDate studyDay, Instant now) {
        QuotaReservation reservation = new QuotaReservation();
        reservation.id = UUID.randomUUID();
        reservation.ownerId = ownerId;
        reservation.task = task;
        reservation.units = units;
        reservation.studyDay = studyDay;
        reservation.status = Status.RESERVED;
        reservation.createdAt = now;
        return reservation;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public QuotaTask getTask() {
        return task;
    }

    public int getUnits() {
        return units;
    }

    public LocalDate getStudyDay() {
        return studyDay;
    }

    public Status getStatus() {
        return status;
    }

    public ReleaseReason getReleaseReason() {
        return releaseReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }
}
