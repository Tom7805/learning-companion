package com.learningcompanion.privacy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Một lần người học yêu cầu xóa tài khoản (NCL-01-CN-009): chờ tới {@code scheduledFor} rồi bị xóa vĩnh viễn, hoặc
 * người học hủy trong thời gian chờ. Bản ghi mất cùng tài khoản khi xóa xong.
 */
@Entity
@Table(name = "account_deletion_requests")
public class AccountDeletionRequest {

    public enum Status { PENDING, CANCELLED }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "scheduled_for", nullable = false, updatable = false)
    private Instant scheduledFor;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    private long version;

    protected AccountDeletionRequest() {
    }

    public static AccountDeletionRequest schedule(UUID userId, Instant now, Instant deleteAt) {
        AccountDeletionRequest request = new AccountDeletionRequest();
        request.id = UUID.randomUUID();
        request.userId = userId;
        request.status = Status.PENDING;
        request.requestedAt = now;
        request.scheduledFor = deleteAt;
        return request;
    }

    public void cancel(Instant now) {
        status = Status.CANCELLED;
        cancelledAt = now;
    }

    public boolean isDue(Instant now) {
        return status == Status.PENDING && !now.isBefore(scheduledFor);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getScheduledFor() {
        return scheduledFor;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }
}
