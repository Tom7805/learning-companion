package com.learningcompanion.library.application;

import com.learningcompanion.library.domain.Document;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Lọc danh sách tài liệu ngay trong cơ sở dữ liệu, luôn kèm điều kiện chủ sở hữu và bỏ tài liệu trong thùng rác. Sắp
 * xếp theo tên dùng thứ tự chữ cái tiếng Việt (Ă sau A, Đ sau D), nên làm trong bộ nhớ sau khi lọc.
 */
@Component
class DocumentSearch {

    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");

    private final EntityManager entityManager;

    DocumentSearch(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    List<Document> search(UUID ownerId, DocumentQuery query) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Document> cq = cb.createQuery(Document.class);
        Root<Document> document = cq.from(Document.class);
        List<Predicate> where = new ArrayList<>();
        where.add(cb.equal(document.get("ownerId"), ownerId));
        where.add(cb.isNull(document.get("trashedAt")));
        switch (query.scope()) {
            case FOLDER -> where.add(query.folderId() == null
                    ? cb.isNull(document.get("folderId"))
                    : cb.equal(document.get("folderId"), query.folderId()));
            case STARRED -> where.add(cb.isTrue(document.get("starred")));
            case ALL, RECENT -> {
            }
        }
        if (!query.types().isEmpty()) {
            where.add(document.get("type").in(query.types()));
        }
        if (!query.statuses().isEmpty()) {
            where.add(document.get("status").in(query.statuses()));
        }
        for (UUID tagId : query.tagIds()) {
            where.add(cb.isMember(tagId, document.get("tagIds")));
        }
        if (query.addedFrom() != null) {
            where.add(cb.greaterThanOrEqualTo(document.get("createdAt"), query.addedFrom()));
        }
        if (query.addedTo() != null) {
            where.add(cb.lessThan(document.get("createdAt"), query.addedTo()));
        }
        cq.select(document).where(where.toArray(Predicate[]::new));
        List<Document> found = new ArrayList<>(entityManager.createQuery(cq).getResultList());
        if (query.scope() == DocumentQuery.Scope.RECENT) {
            found.sort(Comparator.comparing(DocumentSearch::touchedAt).reversed()
                    .thenComparing(Document::getCreatedAt, Comparator.reverseOrder()));
            return found.subList(0, Math.min(found.size(), DocumentQuery.RECENT_LIMIT));
        }
        found.sort(comparator(query));
        return found;
    }

    private static Instant touchedAt(Document document) {
        Instant opened = document.getLastOpenedAt();
        return opened != null && opened.isAfter(document.getCreatedAt()) ? opened : document.getCreatedAt();
    }

    private static Comparator<Document> comparator(DocumentQuery query) {
        boolean asc = query.direction() == DocumentQuery.Direction.ASC;
        Comparator<Document> primary = switch (query.sort()) {
            case NAME -> {
                Collator collator = Collator.getInstance(VIETNAMESE);
                collator.setStrength(Collator.SECONDARY);
                Comparator<Document> byName = Comparator.comparing(Document::getTitle, collator);
                yield asc ? byName : byName.reversed();
            }
            case ADDED -> asc ? Comparator.comparing(Document::getCreatedAt)
                    : Comparator.comparing(Document::getCreatedAt, Comparator.reverseOrder());
            case SIZE -> asc ? Comparator.comparingLong(Document::getSizeBytes)
                    : Comparator.comparingLong(Document::getSizeBytes).reversed();
            // Tài liệu chưa mở lần nào luôn xếp cuối, dù xếp tăng hay giảm.
            case OPENED -> Comparator.comparing(Document::getLastOpenedAt,
                    Comparator.nullsLast(asc ? Comparator.<Instant>naturalOrder() : Comparator.<Instant>reverseOrder()));
        };
        return primary.thenComparing(Document::getCreatedAt, Comparator.reverseOrder())
                .thenComparing(Document::getId);
    }
}
