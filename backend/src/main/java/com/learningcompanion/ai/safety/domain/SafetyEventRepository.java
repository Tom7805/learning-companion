package com.learningcompanion.ai.safety.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SafetyEventRepository extends JpaRepository<SafetyEvent, UUID> {

    List<SafetyEvent> findTop50ByOrderByOccurredAtDesc();
}
