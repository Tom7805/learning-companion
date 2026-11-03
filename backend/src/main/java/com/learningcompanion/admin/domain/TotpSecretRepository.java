package com.learningcompanion.admin.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TotpSecretRepository extends JpaRepository<TotpSecret, UUID> {
}
