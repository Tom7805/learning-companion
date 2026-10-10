package com.learningcompanion.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginThrottleRepository extends JpaRepository<LoginThrottle, String> {
}
