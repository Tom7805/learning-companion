package com.learningcompanion.ai.quota.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotaPolicyRepository extends JpaRepository<QuotaPolicy, Short> {

    default QuotaPolicy current() {
        return findById(QuotaPolicy.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Thiếu dòng hạn mức mặc định (V44)"));
    }
}
