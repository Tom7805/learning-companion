package com.learningcompanion.library.application;

import com.learningcompanion.library.domain.DocumentRepository;
import com.learningcompanion.library.domain.Tag;
import com.learningcompanion.library.domain.TagColor;
import com.learningcompanion.library.domain.TagRepository;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.ownership.OwnerContext;
import com.learningcompanion.shared.ownership.OwnershipGuard;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nhãn có màu của kho tài liệu (NCL-05-CN-001); không giới hạn số nhãn, tên nhãn không trùng nhau. */
@Service
public class TagService {

    private final TagRepository tags;
    private final DocumentRepository documents;
    private final OwnerContext owner;
    private final OwnershipGuard guard;
    private final Clock clock;
    private final EntityManager entityManager;

    public TagService(TagRepository tags, DocumentRepository documents, OwnerContext owner, OwnershipGuard guard,
                      Clock clock, EntityManager entityManager) {
        this.tags = tags;
        this.documents = documents;
        this.owner = owner;
        this.guard = guard;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    /** Mọi nhãn kèm số tài liệu (ngoài thùng rác) đang gắn nhãn đó. */
    @Transactional(readOnly = true)
    public List<Counted> list() {
        UUID ownerId = owner.requireOwnerId();
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : documents.countActiveByOwnerIdGroupedByTag(ownerId)) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return tags.findAllByOwnerIdOrderByNameAsc(ownerId).stream()
                .map(tag -> new Counted(tag, counts.getOrDefault(tag.getId(), 0L)))
                .toList();
    }

    @Transactional
    public Tag create(String name, TagColor color) {
        UUID ownerId = owner.requireOwnerId();
        String clean = requireName(name);
        requireFree(ownerId, clean, null);
        return save(Tag.create(ownerId, clean, color == null ? TagColor.SUN : color, clock.instant()));
    }

    @Transactional
    public Tag update(UUID id, String name, TagColor color, ClientInfo client) {
        Tag tag = guard.require(tags, id, AuditObjectType.LIBRARY_TAG, client);
        String clean = requireName(name);
        requireFree(tag.getOwnerId(), clean, tag.getId());
        tag.update(clean, color == null ? tag.getColor() : color, clock.instant());
        return save(tag);
    }

    /** Xóa nhãn chỉ gỡ nhãn khỏi tài liệu; tài liệu vẫn còn nguyên. */
    @Transactional
    public void delete(UUID id, ClientInfo client) {
        Tag tag = guard.require(tags, id, AuditObjectType.LIBRARY_TAG, client);
        tags.delete(tag);
    }

    /** Mọi mã nhãn phải là nhãn của chính người đang đăng nhập; mã lạ trả lời như không tồn tại. */
    @Transactional(readOnly = true)
    public Set<UUID> requireOwned(UUID ownerId, Collection<UUID> ids) {
        Set<UUID> wanted = new HashSet<>(ids);
        if (wanted.isEmpty()) {
            return wanted;
        }
        if (tags.findAllByOwnerIdAndIdIn(ownerId, wanted).size() != wanted.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return wanted;
    }

    private String requireName(String name) {
        String clean = LibraryNames.clean(name);
        if (clean.isEmpty() || clean.length() > Tag.NAME_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "name");
        }
        return clean;
    }

    private void requireFree(UUID ownerId, String name, UUID exceptId) {
        boolean taken = tags.findAllByOwnerIdOrderByNameAsc(ownerId).stream()
                .anyMatch(tag -> !tag.getId().equals(exceptId)
                        && LibraryNames.key(tag.getName()).equals(LibraryNames.key(name)));
        if (taken) {
            throw new BusinessException(ErrorCode.LIBRARY_TAG_NAME_TAKEN, "name");
        }
    }

    private Tag save(Tag tag) {
        try {
            Tag saved = tags.save(tag);
            entityManager.flush();
            return saved;
        } catch (DataIntegrityViolationException | PersistenceException raced) {
            throw new BusinessException(ErrorCode.LIBRARY_TAG_NAME_TAKEN, "name");
        }
    }

    public record Counted(Tag tag, long documentCount) {
    }
}
