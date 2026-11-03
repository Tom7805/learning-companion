package com.learningcompanion.ai.safety.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentSafetyLevelRepository extends JpaRepository<ContentSafetyLevel, ModerationCategory> {
}
