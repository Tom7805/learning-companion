package com.learningcompanion.ai.quota.application;

import com.learningcompanion.ai.quota.api.dto.AdminQuotaResponse;
import com.learningcompanion.ai.quota.api.dto.QuotaPolicyRequest;
import com.learningcompanion.ai.quota.api.dto.QuotaStatusResponse;
import com.learningcompanion.ai.quota.api.dto.TaskCostsRequest;
import com.learningcompanion.ai.quota.domain.QuotaPolicy;
import com.learningcompanion.ai.quota.domain.QuotaPolicyRepository;
import com.learningcompanion.ai.quota.domain.QuotaTask;
import com.learningcompanion.ai.quota.domain.TaskCost;
import com.learningcompanion.ai.quota.domain.TaskCostRepository;
import com.learningcompanion.ai.quota.domain.UsageLedgerEntry;
import com.learningcompanion.ai.quota.domain.UsageLedgerRepository;
import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditChanges;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.web.ClientInfo;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hạn mức công bằng (NCL-04-CN-003): đồng hồ lượt của người học, và hạn mức chung cùng bảng quy đổi lượt do quản trị
 * viên đặt. Mỗi lần đổi được ghi nhật ký với người thực hiện, giá trị trước và sau và thời điểm (TC-06). Hạn mức mới
 * áp dụng ngay cho tác vụ bắt đầu sau đó; tác vụ đã giữ chỗ giữ nguyên số lượt đã giữ.
 */
@Service
public class QuotaService {

    public static final long MEBIBYTE = 1024L * 1024L;

    private final QuotaPolicyRepository policies;
    private final TaskCostRepository costs;
    private final UsageLedgerRepository ledger;
    private final StorageQuotaService storage;
    private final LearnerDay learnerDay;
    private final UserAccountRepository accounts;
    private final AuditLogger audit;
    private final Clock clock;

    public QuotaService(QuotaPolicyRepository policies, TaskCostRepository costs, UsageLedgerRepository ledger,
                        StorageQuotaService storage, LearnerDay learnerDay, UserAccountRepository accounts,
                        AuditLogger audit, Clock clock) {
        this.policies = policies;
        this.costs = costs;
        this.ledger = ledger;
        this.storage = storage;
        this.learnerDay = learnerDay;
        this.accounts = accounts;
        this.audit = audit;
        this.clock = clock;
    }

    /** Loại tác vụ mới chưa có trong bảng quy đổi thì thêm với số lượt mặc định. Gọi khi ứng dụng khởi động. */
    @Transactional
    public void registerTasks() {
        Set<QuotaTask> known = costs.findAll().stream().map(TaskCost::getTask).collect(Collectors.toSet());
        Instant now = clock.instant();
        for (QuotaTask task : QuotaTask.values()) {
            if (!known.contains(task)) {
                costs.saveAndFlush(TaskCost.initial(task, now));
            }
        }
    }

    // ------------------------------------------------------------------ người học

    /** Đồng hồ lượt; chỉ đọc nên không bao giờ bị chặn, kể cả khi đã hết lượt (TC-03, TC-05). */
    @Transactional(readOnly = true)
    public QuotaStatusResponse status(UUID ownerId) {
        LearnerDay.Today today = learnerDay.today(ownerId);
        int limit = policies.current().getDailyAiUnits();
        UsageLedgerEntry entry = ledger.findById(new UsageLedgerEntry.Key(ownerId, today.day())).orElse(null);
        int used = entry == null ? 0 : entry.getUsedUnits();
        int reserved = entry == null ? 0 : entry.getReservedUnits();
        StorageQuotaService.StorageUsage stored = storage.usage(ownerId);
        return new QuotaStatusResponse(
                new QuotaStatusResponse.Ai(limit, used, reserved, Math.max(0, limit - used - reserved), today.day(),
                        today.zone().getId(), today.resetsAt()),
                new QuotaStatusResponse.Storage(stored.limitBytes(), stored.usedBytes(), stored.remainingBytes(),
                        stored.maxFileBytes()),
                costTable().values().stream()
                        .map(cost -> new QuotaStatusResponse.TaskCostResponse(cost.getTask(), cost.getTask().weight(),
                                cost.getUnits()))
                        .toList());
    }

    // ------------------------------------------------------------------ quản trị viên

    @Transactional(readOnly = true)
    public AdminQuotaResponse adminOverview() {
        QuotaPolicy policy = policies.current();
        Map<QuotaTask, TaskCost> table = costTable();
        Map<UUID, UserAccount> people = people(Stream.concat(Stream.of(policy.getUpdatedBy()),
                table.values().stream().map(TaskCost::getUpdatedBy)));
        return new AdminQuotaResponse(
                new AdminQuotaResponse.Policy(policy.getDailyAiUnits(), policy.getStorageLimitBytes(),
                        policy.getMaxUploadFileBytes(), policy.getUpdatedAt(), actor(policy.getUpdatedBy(), people)),
                table.values().stream()
                        .map(cost -> new AdminQuotaResponse.Cost(cost.getTask(), cost.getTask().weight(),
                                cost.getUnits(), cost.getTask().defaultUnits(), cost.getUpdatedAt(),
                                actor(cost.getUpdatedBy(), people)))
                        .toList());
    }

    @Transactional
    public AdminQuotaResponse updatePolicy(UUID actorId, QuotaPolicyRequest request, ClientInfo client) {
        QuotaPolicy policy = policies.current();
        long storageBytes = request.storageLimitMb() * MEBIBYTE;
        long maxFileMb = request.maxUploadFileMb() == null ? policy.getMaxUploadFileBytes() / MEBIBYTE
                : request.maxUploadFileMb();
        if (maxFileMb > request.storageLimitMb()) {
            // Một tệp không thể lớn hơn cả kho.
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "maxUploadFileMb");
        }
        AuditChanges changes = AuditChanges.of()
                .field("dailyAiUnits", policy.getDailyAiUnits(), request.dailyAiUnits())
                .field("storageLimitMb", policy.getStorageLimitBytes() / MEBIBYTE, request.storageLimitMb())
                .field("maxUploadFileMb", policy.getMaxUploadFileBytes() / MEBIBYTE, maxFileMb);
        if (!changes.isEmpty()) {
            policy.change(request.dailyAiUnits(), storageBytes, maxFileMb * MEBIBYTE, actorId, clock.instant());
            policies.saveAndFlush(policy);
            audit.byUser(actorId, AuditAction.QUOTA_POLICY_CHANGED, AuditObjectType.QUOTA_POLICY, null,
                    changes.toDetails(), client);
        }
        return adminOverview();
    }

    @Transactional
    public AdminQuotaResponse updateCosts(UUID actorId, TaskCostsRequest request, ClientInfo client) {
        Set<QuotaTask> seen = new HashSet<>();
        for (TaskCostsRequest.Entry entry : request.costs()) {
            if (!seen.add(entry.task())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "costs");
            }
        }
        Map<QuotaTask, TaskCost> table = costTable();
        Instant now = clock.instant();
        AuditChanges changes = AuditChanges.of();
        for (TaskCostsRequest.Entry entry : request.costs()) {
            TaskCost cost = table.get(entry.task());
            if (cost.getUnits() != entry.units()) {
                changes.field(entry.task().name(), cost.getUnits(), entry.units());
                cost.change(entry.units(), actorId, now);
                costs.save(cost);
            }
        }
        if (!changes.isEmpty()) {
            costs.flush();
            audit.byUser(actorId, AuditAction.QUOTA_TASK_COSTS_CHANGED, AuditObjectType.QUOTA_POLICY, null,
                    changes.toDetails(), client);
        }
        return adminOverview();
    }

    // ------------------------------------------------------------------ nội bộ

    /** Bảng quy đổi theo thứ tự khai báo; loại chưa kịp đăng ký thì dùng số lượt mặc định. */
    private Map<QuotaTask, TaskCost> costTable() {
        Map<QuotaTask, TaskCost> stored = costs.findAll().stream()
                .collect(Collectors.toMap(TaskCost::getTask, Function.identity()));
        Map<QuotaTask, TaskCost> table = new EnumMap<>(QuotaTask.class);
        Instant now = clock.instant();
        Arrays.stream(QuotaTask.values())
                .forEach(task -> table.put(task, stored.getOrDefault(task, TaskCost.initial(task, now))));
        return table;
    }

    private Map<UUID, UserAccount> people(Stream<UUID> ids) {
        List<UUID> wanted = ids.filter(Objects::nonNull).distinct().toList();
        return accounts.findAllById(wanted).stream().collect(Collectors.toMap(UserAccount::getId, a -> a));
    }

    private static AdminQuotaResponse.Actor actor(UUID id, Map<UUID, UserAccount> people) {
        if (id == null) {
            return null;
        }
        UserAccount account = people.get(id);
        return new AdminQuotaResponse.Actor(id, account == null ? null : account.getDisplayName());
    }
}
