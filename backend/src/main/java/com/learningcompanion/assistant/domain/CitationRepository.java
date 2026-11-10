package com.learningcompanion.assistant.domain;

import com.learningcompanion.shared.ownership.OwnerScopedRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Trích dẫn của câu trả lời, luôn tra kèm chủ sở hữu. */
public interface CitationRepository extends OwnerScopedRepository<Citation> {

    List<Citation> findAllByOwnerIdAndMessageIdInOrderByNumberAsc(UUID ownerId, Collection<UUID> messageIds);
}
