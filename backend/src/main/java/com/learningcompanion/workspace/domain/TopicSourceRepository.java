package com.learningcompanion.workspace.domain;

import com.learningcompanion.shared.ownership.OwnerScopedRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Đoạn nguồn của các chủ đề, luôn tra kèm chủ sở hữu. */
public interface TopicSourceRepository extends OwnerScopedRepository<TopicSource> {

    List<TopicSource> findAllByOwnerIdAndTopicIdIn(UUID ownerId, Collection<UUID> topicIds);
}
