package com.learningcompanion.profile.domain;

import com.learningcompanion.shared.ownership.OwnerScopedRepository;
import java.util.Optional;
import java.util.UUID;

/** Hồ sơ chỉ được đọc kèm chủ sở hữu; xem {@link OwnerScopedRepository}. */
public interface UserProfileRepository extends OwnerScopedRepository<UserProfile> {

    Optional<UserProfile> findByOwnerId(UUID ownerId);
}
