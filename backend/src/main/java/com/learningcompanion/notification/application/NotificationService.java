package com.learningcompanion.notification.application;

import com.learningcompanion.notification.domain.Notification;
import com.learningcompanion.notification.domain.NotificationRepository;
import com.learningcompanion.notification.domain.NotificationTargetType;
import com.learningcompanion.notification.domain.NotificationType;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.ownership.OwnerContext;
import com.learningcompanion.shared.ownership.OwnershipGuard;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Trung tâm thông báo trong ứng dụng (NCL-02-CN-002).
 *
 * <p>{@link #notify} là điểm gửi dùng chung cho mọi chức năng: gọi trong giao dịch của sự kiện gốc, kèm một khóa
 * chống trùng định danh sự kiện đó. Sự kiện gửi lại (thử lại, tác vụ chạy lại) cùng khóa thì không thành thông báo
 * thứ hai (QTN-10). Người học chỉ đọc được thông báo của mình; mã thông báo của người khác được trả lời như không
 * tồn tại và lần từ chối được ghi nhật ký (QTN-08).
 */
@Service
public class NotificationService {

    /** Thông báo được giữ chín mươi ngày rồi tự xóa. */
    public static final Duration RETENTION = Duration.ofDays(90);
    public static final int DEFAULT_PAGE = 20;
    public static final int MAX_PAGE = 50;
    private static final int MAX_DEDUP_KEY = 200;

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final Map<NotificationTargetType, NotificationTargetResolver> resolvers;
    private final OwnerContext owner;
    private final OwnershipGuard guard;
    private final EntityManager entityManager;
    private final JsonMapper json;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, List<NotificationTargetResolver> resolvers,
                               OwnerContext owner, OwnershipGuard guard, EntityManager entityManager, JsonMapper json,
                               ApplicationEventPublisher events, Clock clock) {
        this.notifications = notifications;
        this.resolvers = new EnumMap<>(NotificationTargetType.class);
        for (NotificationTargetResolver resolver : resolvers) {
            if (this.resolvers.put(resolver.type(), resolver) != null) {
                throw new IllegalStateException("Hai bộ phân giải cho cùng loại đối tượng " + resolver.type());
            }
        }
        this.owner = owner;
        this.guard = guard;
        this.entityManager = entityManager;
        this.json = json;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Gửi một thông báo tới người học. Phải gọi trong giao dịch của sự kiện gốc để thông báo chỉ tồn tại khi sự kiện
     * đã thật sự xảy ra.
     *
     * @param dedupKey định danh sự kiện gốc, ví dụ "data-export-ready:{mã gói}"
     * @return false nếu đã có thông báo cùng khóa (lần gửi trùng được bỏ qua)
     */
    @Transactional
    public boolean notify(UUID ownerId, NotificationType type, NotificationTargetType targetType, UUID targetId,
                          Map<String, Object> params, String dedupKey) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(targetType, "targetType");
        if (dedupKey == null || dedupKey.isBlank() || dedupKey.length() > MAX_DEDUP_KEY) {
            throw new IllegalArgumentException("Khóa chống trùng phải có và không quá " + MAX_DEDUP_KEY + " ký tự");
        }
        int inserted = entityManager.createNativeQuery("""
                        insert into notifications
                            (id, owner_id, type, params, target_type, target_id, dedup_key, created_at)
                        values (:id, :ownerId, :type, cast(:params as jsonb), :targetType, :targetId, :dedupKey,
                                :createdAt)
                        on conflict (owner_id, dedup_key) do nothing""")
                .setParameter("id", UUID.randomUUID())
                .setParameter("ownerId", ownerId)
                .setParameter("type", type.name())
                .setParameter("params", json.writeValueAsString(params == null ? Map.of() : params))
                .setParameter("targetType", targetType.name())
                .setParameter("targetId", targetId)
                .setParameter("dedupKey", dedupKey)
                .setParameter("createdAt", clock.instant())
                .executeUpdate();
        if (inserted == 0) {
            log.debug("Bỏ qua thông báo trùng {} của {}", dedupKey, ownerId);
            return false;
        }
        events.publishEvent(new NotificationsChanged(ownerId, true));
        return true;
    }

    /** Một trang thông báo, mới nhất trước; {@code before} là mã thông báo cuối của trang trước. */
    @Transactional(readOnly = true)
    public Page list(UUID before, Integer limit, ClientInfo client) {
        UUID ownerId = owner.requireOwnerId();
        int size = limit == null ? DEFAULT_PAGE : Math.clamp(limit, 1, MAX_PAGE);
        PageRequest page = PageRequest.ofSize(size + 1);
        List<Notification> found;
        if (before == null) {
            found = notifications.findByOwnerIdOrderByCreatedAtDescIdDesc(ownerId, page);
        } else {
            Notification cursor = guard.require(notifications, before, AuditObjectType.NOTIFICATION, client);
            found = notifications.findOlderByOwnerId(ownerId, cursor.getCreatedAt(), cursor.getId(), page);
        }
        boolean more = found.size() > size;
        List<Notification> items = more ? found.subList(0, size) : found;
        UUID next = more ? items.getLast().getId() : null;
        return new Page(items, next, notifications.countByOwnerIdAndReadAtIsNull(ownerId));
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notifications.countByOwnerIdAndReadAtIsNull(owner.requireOwnerId());
    }

    @Transactional(readOnly = true)
    public long unreadCountOf(UUID ownerId) {
        return notifications.countByOwnerIdAndReadAtIsNull(ownerId);
    }

    /**
     * Mở một thông báo: đánh dấu đã đọc và cho biết mở ở đâu. Đối tượng đã bị xóa hay hết hạn thì {@code path}
     * rỗng, giao diện mở trang "nội dung không còn".
     */
    @Transactional
    public Opened open(UUID id, ClientInfo client) {
        Notification notification = guard.require(notifications, id, AuditObjectType.NOTIFICATION, client);
        UUID ownerId = notification.getOwnerId();
        if (notification.markRead(clock.instant())) {
            events.publishEvent(new NotificationsChanged(ownerId, false));
        }
        Optional<String> path = Optional.ofNullable(resolvers.get(notification.getTargetType()))
                .flatMap(resolver -> resolver.pathOf(ownerId, notification.getTargetId()));
        return new Opened(path.orElse(null), notifications.countByOwnerIdAndReadAtIsNull(ownerId));
    }

    @Transactional
    public int markAllRead() {
        UUID ownerId = owner.requireOwnerId();
        int marked = notifications.markAllReadByOwnerId(ownerId, clock.instant());
        if (marked > 0) {
            events.publishEvent(new NotificationsChanged(ownerId, false));
        }
        return marked;
    }

    /**
     * Xóa thông báo quá chín mươi ngày. Việc bảo trì của hệ thống, không trả dữ liệu cho ai nên chạy trên mọi chủ
     * sở hữu.
     */
    @Transactional
    public int purgeOlderThan(Instant cutoff) {
        return entityManager.createQuery("delete from Notification n where n.createdAt < :cutoff")
                .setParameter("cutoff", cutoff)
                .executeUpdate();
    }

    public record Page(List<Notification> items, UUID nextCursor, long unreadCount) {
    }

    public record Opened(String path, long unreadCount) {

        public boolean available() {
            return path != null;
        }
    }
}
