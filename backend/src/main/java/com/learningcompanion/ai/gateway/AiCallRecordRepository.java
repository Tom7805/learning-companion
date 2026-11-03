package com.learningcompanion.ai.gateway;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AiCallRecordRepository extends JpaRepository<AiCallRecord, UUID> {

    List<AiCallRecord> findByOrderByOccurredAtDesc(Pageable page);

    /** Tổng hợp theo khả năng và nhà cung cấp từ một thời điểm. */
    @Query("""
            select r.capability as capability, r.providerCode as providerCode, count(r) as calls,
                   sum(case when r.outcome = com.learningcompanion.ai.gateway.AiCallRecord.Outcome.SUCCESS
                       then 0 else 1 end) as failures,
                   sum(case when r.usedFallback = true then 1 else 0 end) as fallbackCalls,
                   sum(r.inputUnits) as inputUnits, sum(r.outputUnits) as outputUnits,
                   sum(r.costMicroUsd) as costMicroUsd, avg(r.latencyMs) as averageLatencyMs
            from AiCallRecord r
            where r.occurredAt >= :since
            group by r.capability, r.providerCode
            order by r.capability, r.providerCode""")
    List<UsageRow> summarizeSince(Instant since);

    interface UsageRow {
        AiCapability getCapability();

        String getProviderCode();

        long getCalls();

        long getFailures();

        long getFallbackCalls();

        long getInputUnits();

        long getOutputUnits();

        long getCostMicroUsd();

        Double getAverageLatencyMs();
    }
}
