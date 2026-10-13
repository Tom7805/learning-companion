package com.learningcompanion.appshell.domain;

import com.learningcompanion.shared.ownership.OwnerScopedRepository;
import java.util.Optional;
import java.util.UUID;

/** Tùy chọn giao diện chỉ được đọc kèm chủ sở hữu; xem {@link OwnerScopedRepository}. */
public interface UiPreferencesRepository extends OwnerScopedRepository<UiPreferences> {

    Optional<UiPreferences> findByOwnerId(UUID ownerId);
}
