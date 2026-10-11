package com.learningcompanion.shared.ownership;

import java.util.UUID;

/**
 * Dữ liệu học tập thuộc về đúng một người học (QTN-08). Mọi thực thể loại này phải được đọc qua
 * {@link OwnerScopedRepository}, không bao giờ qua một repository cho phép tra theo mã trần.
 */
public interface OwnedEntity {

    UUID getId();

    UUID getOwnerId();
}
