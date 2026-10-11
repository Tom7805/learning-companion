package com.learningcompanion.shared.ownership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.security.AuthenticatedUser;
import com.learningcompanion.shared.security.Role;
import com.learningcompanion.shared.web.ClientInfo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class OwnershipGuardTest {

    private static final ClientInfo CLIENT = new ClientInfo("203.0.113.7", "JUnit");
    private static final UUID ME = UUID.randomUUID();

    @SuppressWarnings("unchecked")
    private final OwnerScopedRepository<Note> repository = mock(OwnerScopedRepository.class);
    private final AccessDeniedLogger deniedLogger = mock(AccessDeniedLogger.class);
    private final OwnershipGuard guard = new OwnershipGuard(new OwnerContext(), deniedLogger);

    @BeforeEach
    void signIn() {
        signInAs(new AuthenticatedUser(ME, UUID.randomUUID(), Role.LEARNER));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownRecordIsReturned() {
        Note mine = new Note(UUID.randomUUID(), ME);
        when(repository.findByIdAndOwnerId(mine.getId(), ME)).thenReturn(Optional.of(mine));

        assertThat(guard.require(repository, mine.getId(), AuditObjectType.DOCUMENT, CLIENT)).isSameAs(mine);
        verify(deniedLogger, never()).notOwner(any(), any(), any(), any());
    }

    @Test
    void someoneElsesRecordLooksMissingAndIsLogged() {
        UUID theirs = UUID.randomUUID();
        when(repository.findByIdAndOwnerId(theirs, ME)).thenReturn(Optional.empty());
        when(repository.existsById(theirs)).thenReturn(true);

        assertThatThrownBy(() -> guard.require(repository, theirs, AuditObjectType.DOCUMENT, CLIENT))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
        verify(deniedLogger).notOwner(ME, AuditObjectType.DOCUMENT, theirs, CLIENT);
    }

    @Test
    void missingRecordIsNotFoundWithoutDenialEntry() {
        UUID missing = UUID.randomUUID();
        when(repository.findByIdAndOwnerId(missing, ME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.require(repository, missing, AuditObjectType.DOCUMENT, CLIENT))
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
        verify(deniedLogger, never()).notOwner(any(), any(), any(), any());
    }

    @Test
    void adminIsOnlyTheOwnerOfTheirOwnData() {
        UUID admin = UUID.randomUUID();
        signInAs(new AuthenticatedUser(admin, UUID.randomUUID(), Role.ADMIN));
        UUID learnersRecord = UUID.randomUUID();
        when(repository.existsById(learnersRecord)).thenReturn(true);

        assertThatThrownBy(() -> guard.require(repository, learnersRecord, AuditObjectType.DOCUMENT, CLIENT))
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
        verify(repository).findByIdAndOwnerId(learnersRecord, admin);
    }

    @Test
    void anonymousCallerIsRejected() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> guard.require(repository, UUID.randomUUID(), AuditObjectType.DOCUMENT, CLIENT))
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    private static void signInAs(AuthenticatedUser user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private record Note(UUID id, UUID ownerId) implements OwnedEntity {

        @Override
        public UUID getId() {
            return id;
        }

        @Override
        public UUID getOwnerId() {
            return ownerId;
        }
    }
}
