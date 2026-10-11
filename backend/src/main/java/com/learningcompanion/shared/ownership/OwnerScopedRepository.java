package com.learningcompanion.shared.ownership;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * Tầng truy vấn dùng chung cho dữ liệu học tập: mọi hàm đọc đều nhận mã chủ sở hữu.
 * Cố ý không kế thừa {@code JpaRepository} để không có {@code findById} hay {@code findAll} trần,
 * nên không chức năng nào lỡ tay đọc được bản ghi của người khác dù đoán được mã.
 *
 * <p>Repository con chỉ được khai báo thêm truy vấn có điều kiện {@code ownerId};
 * {@code OwnershipArchitectureTest} kiểm tra điều này cho mọi thực thể {@link OwnedEntity}.
 */
@NoRepositoryBean
public interface OwnerScopedRepository<T extends OwnedEntity> extends Repository<T, UUID> {

    Optional<T> findByIdAndOwnerId(UUID id, UUID ownerId);

    long countByOwnerId(UUID ownerId);

    /**
     * Chỉ dùng để phân biệt "không tồn tại" với "của người khác" khi ghi nhật ký từ chối;
     * kết quả không bao giờ được trả cho người gọi API.
     */
    boolean existsById(UUID id);

    <S extends T> S save(S entity);

    void delete(T entity);
}
