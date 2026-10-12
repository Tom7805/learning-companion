package com.learningcompanion.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.learningcompanion.privacy.application.ConsentGuard;
import com.learningcompanion.privacy.application.UsageAnalyticsService;
import com.learningcompanion.privacy.domain.ConsentPurpose;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class UsageAnalyticsServiceTest {

    private final ConsentGuard guard = mock(ConsentGuard.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final UsageAnalyticsService analytics = new UsageAnalyticsService(guard, jdbc, Clock.systemUTC());
    private final UUID learner = UUID.randomUUID();

    @Test
    void nothingIsRecordedWithoutConsent() {
        when(guard.allows(learner, ConsentPurpose.ANONYMOUS_USAGE_ANALYTICS)).thenReturn(false);

        assertThat(analytics.record(learner, "page_view", "/library")).isFalse();
        verify(jdbc, never()).update(any(String.class), any(Object[].class));
    }

    @Test
    void idsAndQueryStringsAreStrippedFromRoutes() {
        when(guard.allows(learner, ConsentPurpose.ANONYMOUS_USAGE_ANALYTICS)).thenReturn(true);
        String id = UUID.randomUUID().toString();

        assertThat(analytics.record(learner, "page_view", "/library/" + id + "/notes/42?q=bi-mat#doan")).isTrue();

        verify(jdbc).update(any(String.class), any(UUID.class), org.mockito.ArgumentMatchers.eq("page_view"),
                org.mockito.ArgumentMatchers.eq("/library/:id/notes/:id"), any(java.time.LocalDate.class));
    }

    @Test
    void malformedEventsAreIgnored() {
        when(guard.allows(learner, ConsentPurpose.ANONYMOUS_USAGE_ANALYTICS)).thenReturn(true);

        assertThat(analytics.record(learner, "DROP TABLE", "/")).isFalse();
        assertThat(analytics.record(learner, null, "/")).isFalse();
    }
}
