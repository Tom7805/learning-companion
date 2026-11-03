package com.learningcompanion.ai.safety.application;

import com.learningcompanion.ai.safety.api.dto.ContentSafetyOverviewResponse;
import com.learningcompanion.ai.safety.api.dto.SafetyLevelsRequest;
import com.learningcompanion.ai.safety.domain.ContentSafetyLevel;
import com.learningcompanion.ai.safety.domain.ContentSafetyLevelRepository;
import com.learningcompanion.ai.safety.domain.InjectionSignal;
import com.learningcompanion.ai.safety.domain.ModerationCategory;
import com.learningcompanion.ai.safety.domain.SafetyEvent;
import com.learningcompanion.ai.safety.domain.SafetyEventRepository;
import com.learningcompanion.ai.safety.domain.SafetyLevel;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ngưỡng lọc và sổ can thiệp của bộ lọc an toàn (NCL-04-CN-004). Mỗi lần bộ lọc chặn, trả lời ân cần hay gắn cờ một
 * đoạn nguồn đều được ghi một dòng sự kiện và một dòng nhật ký thao tác, chỉ với loại vi phạm và mã đối tượng (QTN-15).
 * Chỉ quản trị viên đổi được ngưỡng; mỗi lần đổi ghi giá trị trước và sau.
 */
@Service
public class ContentSafetyService {

    public static final SafetyLevel DEFAULT_LEVEL = SafetyLevel.STANDARD;

    private final ContentSafetyLevelRepository levels;
    private final SafetyEventRepository events;
    private final UserAccountRepository accounts;
    private final AuditLogger audit;
    private final Clock clock;

    public ContentSafetyService(ContentSafetyLevelRepository levels, SafetyEventRepository events,
                                UserAccountRepository accounts, AuditLogger audit, Clock clock) {
        this.levels = levels;
        this.events = events;
        this.accounts = accounts;
        this.audit = audit;
        this.clock = clock;
    }

    /** Ngưỡng hiện hành của mọi nhóm có ngưỡng; nhóm chưa có dòng dùng ngưỡng mặc định. Đọc mỗi lần, không đệm. */
    @Transactional(readOnly = true)
    public Map<ModerationCategory, SafetyLevel> currentLevels() {
        Map<ModerationCategory, SafetyLevel> current = new EnumMap<>(ModerationCategory.class);
        for (ModerationCategory category : configurable()) {
            current.put(category, DEFAULT_LEVEL);
        }
        levels.findAll().forEach(row -> current.put(row.getCategory(), row.getLevel()));
        return current;
    }

    @Transactional(readOnly = true)
    public ContentSafetyOverviewResponse overview() {
        Map<ModerationCategory, ContentSafetyLevel> rows = levels.findAll().stream()
                .collect(Collectors.toMap(ContentSafetyLevel::getCategory, Function.identity()));
        Map<UUID, UserAccount> people = accounts.findAllById(rows.values().stream()
                        .map(ContentSafetyLevel::getUpdatedBy).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(UserAccount::getId, a -> a));
        List<ContentSafetyOverviewResponse.Level> list = configurable().stream().map(category -> {
            ContentSafetyLevel row = rows.get(category);
            if (row == null) {
                return new ContentSafetyOverviewResponse.Level(category, DEFAULT_LEVEL, DEFAULT_LEVEL, null, null);
            }
            UserAccount by = row.getUpdatedBy() == null ? null : people.get(row.getUpdatedBy());
            return new ContentSafetyOverviewResponse.Level(category, row.getLevel(), DEFAULT_LEVEL,
                    row.getUpdatedAt(), row.getUpdatedBy() == null ? null
                    : new ContentSafetyOverviewResponse.Actor(row.getUpdatedBy(),
                            by == null ? null : by.getDisplayName()));
        }).toList();
        return new ContentSafetyOverviewResponse(list, events.findTop50ByOrderByOccurredAtDesc().stream()
                .map(ContentSafetyOverviewResponse.Event::of).toList());
    }

    @Transactional
    public ContentSafetyOverviewResponse updateLevels(UUID actorId, SafetyLevelsRequest request, ClientInfo client) {
        Set<ModerationCategory> seen = new HashSet<>();
        for (SafetyLevelsRequest.Entry entry : request.levels()) {
            if (!entry.category().configurable() || !seen.add(entry.category())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "levels");
            }
        }
        Map<ModerationCategory, ContentSafetyLevel> rows = levels.findAll().stream()
                .collect(Collectors.toMap(ContentSafetyLevel::getCategory, Function.identity()));
        Instant now = clock.instant();
        AuditChanges changes = AuditChanges.of();
        for (SafetyLevelsRequest.Entry entry : request.levels()) {
            ContentSafetyLevel row = rows.get(entry.category());
            if (row == null) {
                throw new IllegalStateException("Thiếu dòng ngưỡng lọc (V45) cho " + entry.category());
            }
            if (row.getLevel() != entry.level()) {
                changes.field(entry.category().name(), row.getLevel().name(), entry.level().name());
                row.change(entry.level(), actorId, now);
                levels.save(row);
            }
        }
        if (!changes.isEmpty()) {
            levels.flush();
            audit.byUser(actorId, AuditAction.CONTENT_SAFETY_LEVELS_CHANGED, AuditObjectType.CONTENT_SAFETY, null,
                    changes.toDetails(), client);
        }
        return overview();
    }

    /**
     * Ghi một lần can thiệp. Đối tượng trống thì nhật ký trỏ tới chính dòng sự kiện, để quản trị viên vẫn tra được.
     *
     * @return mã sự kiện
     */
    @Transactional
    public UUID record(UUID actorId, String feature, SafetyEvent.Kind kind, ModerationCategory category,
                       InjectionSignal signal, AuditObjectType objectType, UUID objectId, ClientInfo client,
                       Map<String, Object> extra) {
        SafetyEvent event = events.save(SafetyEvent.of(clock.instant(), actorId, feature, kind, category, signal,
                objectType, objectId));
        AuditObjectType auditType = objectType == null ? AuditObjectType.AI_SAFETY_EVENT : objectType;
        UUID auditId = objectType == null ? event.getId() : objectId;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("feature", feature);
        if (category != null) {
            details.put("category", category.name());
        }
        if (signal != null) {
            details.put("signal", signal.name());
        }
        details.put("eventId", event.getId().toString());
        details.putAll(extra);
        AuditAction action = switch (kind) {
            case INPUT_BLOCKED -> AuditAction.AI_INPUT_BLOCKED;
            case OUTPUT_BLOCKED -> AuditAction.AI_OUTPUT_BLOCKED;
            case SELF_HARM_SUPPORT -> AuditAction.AI_SELF_HARM_SUPPORT_GIVEN;
            case INJECTION_FLAGGED -> AuditAction.AI_SOURCE_INJECTION_FLAGGED;
        };
        if (actorId == null) {
            audit.bySystem(action, auditType, auditId, details);
        } else {
            audit.byUser(actorId, action, auditType, auditId, details, client);
        }
        return event.getId();
    }

    private static List<ModerationCategory> configurable() {
        return Arrays.stream(ModerationCategory.values()).filter(ModerationCategory::configurable).toList();
    }
}
