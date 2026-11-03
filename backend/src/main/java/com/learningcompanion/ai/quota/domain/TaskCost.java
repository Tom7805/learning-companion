package com.learningcompanion.ai.quota.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Một dòng của bảng quy đổi lượt (NCL-04-CN-003): loại tác vụ và số lượt mỗi lần chạy. */
@Entity
@Table(name = "quota_task_costs")
public class TaskCost {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 60)
    private QuotaTask task;

    @Column(nullable = false)
    private int units;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private long version;

    protected TaskCost() {
    }

    public static TaskCost initial(QuotaTask task, Instant now) {
        TaskCost cost = new TaskCost();
        cost.task = task;
        cost.units = task.defaultUnits();
        cost.updatedAt = now;
        return cost;
    }

    public void change(int units, UUID actorId, Instant now) {
        this.units = units;
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    public QuotaTask getTask() {
        return task;
    }

    public int getUnits() {
        return units;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}
