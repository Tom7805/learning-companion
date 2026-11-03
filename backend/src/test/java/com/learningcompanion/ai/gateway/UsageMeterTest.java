package com.learningcompanion.ai.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.learningcompanion.ai.gateway.model.AiUsage;
import com.learningcompanion.ai.provider.AdapterType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UsageMeterTest {

    @Test
    void costIsPricePerMillionTimesUnitsInMicroDollars() {
        AiProviderConfig provider = AiProviderConfig.declare("p", AdapterType.MOCK, Instant.EPOCH);
        provider.describe("P", null, null, null, null, Set.of(AiCapability.TEXT_GENERATION), null,
                new BigDecimal("0.15"), new BigDecimal("0.6"), Instant.EPOCH);

        // 1 000 đơn vị vào × 0,15 + 500 đơn vị ra × 0,6 = 150 + 300 phần triệu đô la.
        assertThat(UsageMeter.costMicroUsd(provider, new AiUsage(1_000, 500))).isEqualTo(450);
        assertThat(UsageMeter.costMicroUsd(null, new AiUsage(1_000, 500))).isZero();
    }
}
