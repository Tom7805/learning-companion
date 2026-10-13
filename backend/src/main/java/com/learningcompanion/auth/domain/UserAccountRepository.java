package com.learningcompanion.auth.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    /** Nhận địa chỉ đã chuẩn hóa bằng EmailNormalizer. */
    Optional<UserAccount> findByEmail(String email);

    /** Khóa dòng tài khoản tới hết giao dịch, để hai yêu cầu song song của cùng một người được xử lý lần lượt. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from UserAccount a where a.id = :id")
    Optional<UserAccount> lockById(@Param("id") UUID id);
}
