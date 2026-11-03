package com.learningcompanion.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.learningcompanion.shared.web.ClientInfo;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

class AuditLoggerTest {

    private static final Instant NOW = Instant.parse("2026-10-14T03:00:00Z");
    private static final ClientInfo CLIENT = new ClientInfo("203.0.113.7", "JUnit");

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final AuditLogger logger = new AuditLogger(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void storesMetadataWithTimeActorAndClient() {
        UUID actor = UUID.randomUUID();
        UUID object = UUID.randomUUID();

        logger.byUser(actor, AuditAction.PROFILE_UPDATED, AuditObjectType.USER_ACCOUNT, object,
                Map.of("fields", List.of("displayName")), CLIENT);

        AuditLog saved = saved();
        assertThat(saved.getOccurredAt()).isEqualTo(NOW);
        assertThat(saved.getActorType()).isEqualTo(AuditLog.ActorType.USER);
        assertThat(saved.getActorId()).isEqualTo(actor);
        assertThat(saved.getObjectId()).isEqualTo(object);
        assertThat(saved.getIpAddress()).isEqualTo("203.0.113.7");
        assertThat(saved.getDetails()).containsExactly(Map.entry("fields", List.of("displayName")));
    }

    @Test
    void contentFieldsAreDroppedAndNamedEvenWhenNested() {
        logger.bySystem(AuditAction.DATA_EXPORT_EXPIRED, AuditObjectType.DATA_EXPORT, UUID.randomUUID(),
                Map.of("status", "EXPIRED", "noteText", "Ghi chép riêng",
                        "items", List.of(Map.of("id", "a1", "answer_body", "Đáp án"))));

        Map<String, Object> details = saved().getDetails();
        assertThat(details).containsEntry("status", "EXPIRED").doesNotContainKey("noteText");
        assertThat(details.get("items")).isEqualTo(List.of(Map.of("id", "a1")));
        assertThat(details.get(ContentFieldStripper.REMOVED_FIELDS))
                .isEqualTo(List.of("items[].answer_body", "noteText"));
        assertThat(details.toString()).doesNotContain("Ghi chép riêng", "Đáp án");
    }

    @Test
    void storageFailureReachesTheCallerSoItsTransactionRollsBack() {
        when(repository.save(any())).thenThrow(new DataAccessResourceFailureException("disk full"));

        assertThatThrownBy(() -> logger.byAnonymous(AuditAction.LOGIN_FAILED, AuditObjectType.USER_ACCOUNT, null,
                Map.of(), CLIENT)).isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void contentKeysAreMatchedByWordNotBySubstring() {
        assertThat(List.of("content", "note_text", "noteText", "user-prompt", "answerBody", "API_KEY", "apiKey",
                "title", "Description")).allMatch(ContentFieldStripper::isContentKey);
        assertThat(List.of("context", "status", "reason", "failedCount", "fields", "changes", "method", "path",
                "titled", "contextual")).noneMatch(ContentFieldStripper::isContentKey);
    }

    @Test
    void cleanDetailsAreKeptAsIs() {
        assertThat(ContentFieldStripper.strip(Map.of("reason", "LOCKED"))).isEqualTo(Map.of("reason", "LOCKED"));
        assertThat(ContentFieldStripper.strip(null)).isEmpty();
    }

    @Test
    void changesRecordOnlyFieldsThatReallyChanged() {
        AuditChanges changes = AuditChanges.of()
                .field("themeMode", "SYSTEM", "DARK")
                .field("fontSize", "MEDIUM", "MEDIUM");

        assertThat(changes.isEmpty()).isFalse();
        assertThat(changes.toDetails()).isEqualTo(Map.of("changes",
                List.of(Map.of("field", "themeMode", "before", "SYSTEM", "after", "DARK"))));
        assertThat(AuditChanges.of().field("a", 1, 1).isEmpty()).isTrue();
    }

    private AuditLog saved() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
