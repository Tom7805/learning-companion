package com.learningcompanion.ai.gateway;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiGatewayAlertRepository extends JpaRepository<AiGatewayAlert, UUID> {

    List<AiGatewayAlert> findByAcknowledgedAtIsNullOrderByOpenedAtDesc();

    boolean existsByCapabilityAndProviderCodeAndAcknowledgedAtIsNull(AiCapability capability, String providerCode);
}
