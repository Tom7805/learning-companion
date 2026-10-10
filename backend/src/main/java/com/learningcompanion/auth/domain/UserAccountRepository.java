package com.learningcompanion.auth.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    /** Nhận địa chỉ đã chuẩn hóa bằng EmailNormalizer. */
    Optional<UserAccount> findByEmail(String email);
}
