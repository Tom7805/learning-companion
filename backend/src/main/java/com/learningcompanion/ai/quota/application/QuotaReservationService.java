package com.learningcompanion.ai.quota.application;

import com.learningcompanion.ai.quota.domain.QuotaPolicyRepository;
import com.learningcompanion.ai.quota.domain.QuotaReservation;
import com.learningcompanion.ai.quota.domain.QuotaReservationRepository;
import com.learningcompanion.ai.quota.domain.QuotaTask;
import com.learningcompanion.ai.quota.domain.TaskCost;
import com.learningcompanion.ai.quota.domain.TaskCostRepository;
import com.learningcompanion.ai.quota.domain.UsageLedgerEntry;
import com.learningcompanion.ai.quota.domain.UsageLedgerRepository;
import com.learningcompanion.notification.application.NotificationService;
import com.learningcompanion.notification.domain.NotificationTargetType;
import com.learningcompanion.notification.domain.NotificationType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sổ lượt trí tuệ nhân tạo (NCL-04-CN-003, QTN-13). Mọi chức năng tốn lượt đều chạy qua đây trước khi gọi lớp kết
 * nối, thường bằng {@link #metered}: giữ chỗ đủ số lượt theo bảng quy đổi khi tác vụ bắt đầu, trừ khi hoàn tất, hoàn
 * lại khi lỗi hoặc bị hủy. Hết lượt chỉ chặn tác vụ mới tốn lượt; thao tác đọc không bao giờ đi qua đây.
 *
 * <p>Mỗi bước chạy trong giao dịch riêng và ngắn (kể cả khi được gọi bên trong giao dịch khác): tác vụ có thể chạy lâu
 * (sinh nội dung theo luồng) mà không giữ khóa hay giao dịch trong lúc chờ nhà cung cấp, và lượt đã hoàn lại vẫn được
 * hoàn dù giao dịch của chức năng gọi bị hủy.
 */
@Service
public class QuotaReservationService {

    /** Lần giữ chỗ chưa chốt sau ngần này thì coi như tác vụ đã chết (máy chủ khởi động lại giữa chừng). */
    public static final Duration STALE_AFTER = Duration.ofMinutes(30);

    private final QuotaPolicyRepository policies;
    private final TaskCostRepository costs;
    private final UsageLedgerRepository ledger;
    private final QuotaReservationRepository reservations;
    private final NotificationService notifications;
    private final LearnerDay learnerDay;
    private final Clock clock;
    private final TransactionTemplate tx;

    public QuotaReservationService(QuotaPolicyRepository policies, TaskCostRepository costs,
                                   UsageLedgerRepository ledger, QuotaReservationRepository reservations,
                                   NotificationService notifications, LearnerDay learnerDay, Clock clock,
                                   PlatformTransactionManager transactions) {
        this.policies = policies;
        this.costs = costs;
        this.ledger = ledger;
        this.reservations = reservations;
        this.notifications = notifications;
        this.learnerDay = learnerDay;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Chạy một tác vụ tốn lượt: giữ chỗ, chạy, trừ khi xong. Tác vụ ném lỗi thì lượt được hoàn lại đủ và người học
     * nhận thông báo không bị trừ lượt; lỗi nghiệp vụ được ném lại kèm {@code quotaRefunded} và số lượt đã hoàn để
     * giao diện báo ngay.
     *
     * @throws BusinessException {@link ErrorCode#AI_QUOTA_EXHAUSTED} nếu không còn đủ lượt, tác vụ không chạy
     */
    public <T> T metered(UUID ownerId, QuotaTask task, Supplier<T> work) {
        Reservation reservation = reserve(ownerId, task);
        T result;
        try {
            result = work.get();
        } catch (BusinessException ex) {
            release(reservation.id(), QuotaReservation.ReleaseReason.FAILED);
            Map<String, Object> details = new LinkedHashMap<>(ex.details() == null ? Map.of() : ex.details());
            details.put("quotaRefunded", true);
            details.put("refundedUnits", reservation.units());
            throw new BusinessException(ex.code(), ex.field(), details);
        } catch (RuntimeException | Error ex) {
            release(reservation.id(), QuotaReservation.ReleaseReason.FAILED);
            throw ex;
        }
        commit(reservation.id());
        return result;
    }

    /** Giữ chỗ đủ số lượt của tác vụ trong ngày học hiện tại của người học. */
    public Reservation reserve(UUID ownerId, QuotaTask task) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(task, "task");
        return tx.execute(status -> doReserve(ownerId, task));
    }

    private Reservation doReserve(UUID ownerId, QuotaTask task) {
        LearnerDay.Today today = learnerDay.today(ownerId);
        int limit = policies.current().getDailyAiUnits();
        int units = costs.findById(task).map(TaskCost::getUnits).orElse(task.defaultUnits());
        ledger.ensureRow(ownerId, today.day());
        if (ledger.reserve(ownerId, today.day(), units, limit) == 0) {
            UsageLedgerEntry entry = ledger.findById(new UsageLedgerEntry.Key(ownerId, today.day())).orElse(null);
            int taken = entry == null ? 0 : entry.getUsedUnits() + entry.getReservedUnits();
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("task", task.name());
            details.put("requiredUnits", units);
            details.put("remainingUnits", Math.max(0, limit - taken));
            details.put("dailyUnits", limit);
            details.put("resetsAt", today.resetsAt().toString());
            details.put("timeZone", today.zone().getId());
            throw new BusinessException(ErrorCode.AI_QUOTA_EXHAUSTED, null, details);
        }
        QuotaReservation reservation = reservations.save(
                QuotaReservation.reserve(ownerId, task, units, today.day(), today.now()));
        return new Reservation(reservation.getId(), ownerId, task, units);
    }

    /** Tác vụ hoàn tất: trừ hẳn số lượt đã giữ. Gọi lại lần nữa thì không trừ thêm. */
    public boolean commit(UUID reservationId) {
        return Boolean.TRUE.equals(tx.execute(status -> doCommit(reservationId)));
    }

    private boolean doCommit(UUID reservationId) {
        QuotaReservation reservation = reservations.findById(reservationId).orElse(null);
        if (reservation == null || reservations.markCommitted(reservationId, clock.instant()) == 0) {
            return false;
        }
        ledger.commit(reservation.getOwnerId(), reservation.getStudyDay(), reservation.getUnits());
        return true;
    }

    /**
     * Hoàn lại số lượt đã giữ. Gọi lại lần nữa, hay gọi sau khi đã trừ, thì không có tác dụng. Lỗi do dịch vụ bên ngoài
     * thì người học nhận một thông báo trong ứng dụng là không bị trừ lượt.
     */
    public boolean release(UUID reservationId, QuotaReservation.ReleaseReason reason) {
        return Boolean.TRUE.equals(tx.execute(status -> doRelease(reservationId, reason)));
    }

    private boolean doRelease(UUID reservationId, QuotaReservation.ReleaseReason reason) {
        QuotaReservation reservation = reservations.findById(reservationId).orElse(null);
        if (reservation == null || reservations.markReleased(reservationId, reason, clock.instant()) == 0) {
            return false;
        }
        ledger.release(reservation.getOwnerId(), reservation.getStudyDay(), reservation.getUnits());
        if (reason == QuotaReservation.ReleaseReason.FAILED) {
            notifications.notify(reservation.getOwnerId(), NotificationType.AI_TASK_REFUNDED,
                    NotificationTargetType.USAGE, null,
                    Map.of("task", reservation.getTask().name(), "units", reservation.getUnits()),
                    "ai-task-refunded:" + reservationId);
        }
        return true;
    }

    /** Hoàn lại các lần giữ chỗ treo quá lâu; trả về số lần đã hoàn. */
    public int releaseStale(Instant before, int batch) {
        int released = 0;
        for (UUID id : tx.execute(status -> reservations.findStaleIds(before, Limit.of(batch)))) {
            if (release(id, QuotaReservation.ReleaseReason.EXPIRED)) {
                released++;
            }
        }
        return released;
    }

    /** Một lần giữ chỗ đang mở; chức năng gọi giữ mã để chốt hoặc hoàn lại. */
    public record Reservation(UUID id, UUID ownerId, QuotaTask task, int units) {
    }
}
