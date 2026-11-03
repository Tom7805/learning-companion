package com.learningcompanion.ai.gateway;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiProviderConfigRepository extends JpaRepository<AiProviderConfig, UUID> {

    Optional<AiProviderConfig> findByCode(String code);

    boolean existsByCode(String code);

    List<AiProviderConfig> findAllByOrderByCreatedAtAscCodeAsc();
}
