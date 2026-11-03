package com.learningcompanion.admin.application;

import com.learningcompanion.admin.api.dto.AuditLogQuery;
import com.learningcompanion.admin.api.dto.AuditLogResponse;
import com.learningcompanion.auth.application.EmailNormalizer;
import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.auth.infrastructure.UserAgentParser;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditLog;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tra cứu nhật ký thao tác cho quản trị viên (NCL-03-CN-001): lọc theo tài khoản, nhóm hay loại thao tác, loại đối
 * tượng và khoảng thời gian, mới nhất trước, phân trang theo con trỏ để trang sau không lệch khi có dòng mới ghi
 * thêm. Bản thân lần tra cứu cũng được ghi lại kèm điều kiện lọc (TC-06).
 */
@Service
public class AuditLogQueryService {

    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 100;

    private final EntityManager entityManager;
    private final UserAccountRepository accounts;
    private final UserAgentParser userAgents;
    private final AuditLogger audit;

    public AuditLogQueryService(EntityManager entityManager, UserAccountRepository accounts,
                                UserAgentParser userAgents, AuditLogger audit) {
        this.entityManager = entityManager;
        this.accounts = accounts;
        this.userAgents = userAgents;
        this.audit = audit;
    }

    @Transactional
    public AuditLogResponse search(UUID adminId, AuditLogQuery query, ClientInfo client) {
        int limit = limitOf(query.limit());
        if (query.from() != null && query.to() != null && !query.from().isBefore(query.to())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "to");
        }
        Optional<UUID> accountId = resolveAccount(query.account());
        audit.byUser(adminId, AuditAction.AUDIT_LOG_SEARCHED, AuditObjectType.AUDIT_LOG, null,
                searchDetails(query, accountId), client);
        if (hasText(query.account()) && accountId.isEmpty()) {
            return new AuditLogResponse(List.of(), null);
        }

        List<AuditLog> rows = find(query, accountId.orElse(null), limit + 1);
        boolean hasMore = rows.size() > limit;
        List<AuditLog> page = hasMore ? rows.subList(0, limit) : rows;
        Map<UUID, AuditLogResponse.Account> people = peopleIn(page);
        List<AuditLogResponse.Entry> items = page.stream().map(row -> entry(row, people)).toList();
        return new AuditLogResponse(items, hasMore ? page.getLast().getId() : null);
    }

    private List<AuditLog> find(AuditLogQuery query, UUID accountId, int max) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<AuditLog> cq = cb.createQuery(AuditLog.class);
        Root<AuditLog> row = cq.from(AuditLog.class);
        List<Predicate> where = new ArrayList<>();

        if (accountId != null) {
            where.add(cb.or(
                    cb.equal(row.get("actorId"), accountId),
                    cb.and(cb.equal(row.get("objectType"), AuditObjectType.USER_ACCOUNT),
                            cb.equal(row.get("objectId"), accountId))));
        }
        if (query.category() != null) {
            where.add(row.get("action").in(actionsIn(query.category())));
        }
        if (query.action() != null) {
            where.add(cb.equal(row.get("action"), query.action()));
        }
        if (query.objectType() != null) {
            where.add(cb.equal(row.get("objectType"), query.objectType()));
        }
        if (query.from() != null) {
            where.add(cb.greaterThanOrEqualTo(row.get("occurredAt"), query.from()));
        }
        if (query.to() != null) {
            where.add(cb.lessThan(row.get("occurredAt"), query.to()));
        }
        if (query.before() != null) {
            AuditLog cursor = entityManager.find(AuditLog.class, query.before());
            if (cursor == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "before");
            }
            where.add(cb.or(
                    cb.lessThan(row.get("occurredAt"), cursor.getOccurredAt()),
                    cb.and(cb.equal(row.get("occurredAt"), cursor.getOccurredAt()),
                            cb.lessThan(row.get("id"), cursor.getId()))));
        }

        cq.select(row).where(where.toArray(Predicate[]::new))
                .orderBy(cb.desc(row.get("occurredAt")), cb.desc(row.get("id")));
        return entityManager.createQuery(cq).setMaxResults(max).getResultList();
    }

    private Optional<UUID> resolveAccount(String account) {
        if (!hasText(account)) {
            return Optional.empty();
        }
        String value = account.strip();
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException notAnId) {
            return accounts.findByEmail(EmailNormalizer.normalize(value)).map(UserAccount::getId);
        }
    }

    /** Ghi điều kiện lọc; địa chỉ thư được thay bằng mã tài khoản để nhật ký không chép thêm dữ liệu cá nhân. */
    private static Map<String, Object> searchDetails(AuditLogQuery query, Optional<UUID> accountId) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (hasText(query.account())) {
            details.put("account", accountId.map(UUID::toString).orElse("unknown"));
        }
        putIfPresent(details, "category", query.category());
        putIfPresent(details, "action", query.action());
        putIfPresent(details, "objectType", query.objectType());
        putIfPresent(details, "from", query.from());
        putIfPresent(details, "to", query.to());
        putIfPresent(details, "before", query.before());
        return details;
    }

    private Map<UUID, AuditLogResponse.Account> peopleIn(List<AuditLog> rows) {
        Set<UUID> ids = new HashSet<>();
        for (AuditLog row : rows) {
            if (row.getActorId() != null) {
                ids.add(row.getActorId());
            }
            if (row.getObjectType() == AuditObjectType.USER_ACCOUNT && row.getObjectId() != null) {
                ids.add(row.getObjectId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return accounts.findAllById(ids).stream()
                .map(a -> new AuditLogResponse.Account(a.getId(), a.getEmail(), a.getDisplayName()))
                .collect(Collectors.toMap(AuditLogResponse.Account::id, Function.identity()));
    }

    private AuditLogResponse.Entry entry(AuditLog row, Map<UUID, AuditLogResponse.Account> people) {
        AuditLogResponse.Account actor = row.getActorId() == null ? null : people.get(row.getActorId());
        AuditLogResponse.Account subject = row.getObjectType() == AuditObjectType.USER_ACCOUNT
                && row.getObjectId() != null ? people.get(row.getObjectId()) : null;
        return new AuditLogResponse.Entry(row.getId(), row.getOccurredAt(), row.getActorType(), row.getActorId(),
                actor, row.getAction(), row.getAction().category(), row.getAction().outcome(), row.getObjectType(),
                row.getObjectId(), subject, device(row.getUserAgent()), row.getIpAddress(), row.getDetails(),
                row.getRequestId());
    }

    private AuditLogResponse.Device device(String userAgent) {
        if (!hasText(userAgent)) {
            return null;
        }
        UserAgentParser.Parsed parsed = userAgents.parse(userAgent);
        return new AuditLogResponse.Device(parsed.browser(), parsed.operatingSystem(), parsed.deviceType().name());
    }

    private static List<AuditAction> actionsIn(AuditAction.Category category) {
        return Arrays.stream(AuditAction.values()).filter(a -> a.category() == category).toList();
    }

    private static int limitOf(Integer requested) {
        if (requested == null) {
            return DEFAULT_LIMIT;
        }
        if (requested < 1 || requested > MAX_LIMIT) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "limit");
        }
        return requested;
    }

    private static void putIfPresent(Map<String, Object> details, String key, Object value) {
        if (value != null) {
            details.put(key, value.toString());
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
