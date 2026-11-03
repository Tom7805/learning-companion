package com.learningcompanion.ai.gateway;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AiCapabilityRouteRepository extends JpaRepository<AiCapabilityRoute, AiCapability> {

    @Query("select r from AiCapabilityRoute r where r.primaryProviderId = :providerId or r.fallbackProviderId = :providerId")
    List<AiCapabilityRoute> findUsing(UUID providerId);
}
