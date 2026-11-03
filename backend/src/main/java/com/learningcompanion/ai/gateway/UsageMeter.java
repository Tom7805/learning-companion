package com.learningcompanion.ai.gateway;

import com.learningcompanion.ai.gateway.model.AiUsage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi sổ đo mức sử dụng: mỗi lời gọi một dòng (kèm số phiên bản cấu hình của chức năng nếu có), trong giao dịch riêng để lời gọi thất bại hay chức năng gọi bị hủy vẫn
 * được đo. Chi phí tính theo giá mỗi triệu đơn vị của nhà cung cấp, lưu bằng phần triệu đô la.
 */
@Component
public class UsageMeter {

    private final AiCallRecordRepository records;
    private final Clock clock;

    public UsageMeter(AiCallRecordRepository records, Clock clock) {
        this.records = records;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiCallRecord record(AiCapability capability, String feature, Integer configVersion,
                               AiProviderConfig provider, AiCallRecord.Outcome outcome, boolean usedFallback,
                               int attempts, AiUsage usage, long latencyMs, String errorCode) {
        AiUsage measured = usage == null ? AiUsage.NONE : usage;
        return records.save(new AiCallRecord(clock.instant(), capability, feature, configVersion,
                provider == null ? null : provider.getCode(), outcome, usedFallback, attempts,
                measured.inputUnits(), measured.outputUnits(), (int) Math.min(Integer.MAX_VALUE, latencyMs),
                costMicroUsd(provider, measured), errorCode));
    }

    /** Giá theo đô la cho một triệu đơn vị, nên mỗi đơn vị tốn đúng "giá" phần triệu đô la. */
    static long costMicroUsd(AiProviderConfig provider, AiUsage usage) {
        if (provider == null) {
            return 0;
        }
        return provider.getInputPricePerMillion().multiply(BigDecimal.valueOf(usage.inputUnits()))
                .add(provider.getOutputPricePerMillion().multiply(BigDecimal.valueOf(usage.outputUnits())))
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
