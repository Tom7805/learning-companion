package com.learningcompanion.ai.quota.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskCostRepository extends JpaRepository<TaskCost, QuotaTask> {
}
