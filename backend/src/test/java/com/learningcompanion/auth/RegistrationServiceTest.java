package com.learningcompanion.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.learningcompanion.auth.application.AuthMailNotifier;
import com.learningcompanion.auth.application.BreachedPasswordChecker;
import com.learningcompanion.auth.application.EmailVerificationService;
import com.learningcompanion.auth.application.PasswordPolicyValidator;
import com.learningcompanion.auth.application.RegistrationService;
import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.privacy.application.ConsentService;
import com.learningcompanion.privacy.application.TermsVersionService;
import com.learningcompanion.privacy.domain.TermsVersion;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.exception.RateLimitExceededException;
import com.learningcompanion.shared.security.PasswordHasher;
import com.learningcompanion.shared.security.RateLimiter;
import com.learningcompanion.shared.web.ClientInfo;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class RegistrationServiceTest {

    private static final String VERSION = "2026-10-01";
    private static final ClientInfo CLIENT = new ClientInfo("203.0.113.7", "JUnit");
    private static final Instant NOW = Instant.parse("2026-10-10T03:00:00Z");

    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final PasswordHasher passwordHasher = mock(PasswordHasher.class);
    private final TermsVersionService termsVersionService = mock(TermsVersionService.class);
    private final ConsentService consentService = mock(ConsentService.class);
    private final EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
    private final AuditLogger audit = mock(AuditLogger.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private RateLimiter rateLimiter;
    private RegistrationService service;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiter(clock);
        AppProperties properties = new AppProperties("http://localhost:5173",
                new AppProperties.Auth(Duration.ofHours(24), Duration.ofSeconds(60), Duration.ofHours(2),
                        "lc_session", false, 3, 20, Duration.ofHours(1), Duration.ofDays(30), 5,
                        Duration.ofMinutes(15), 100, "lc_device"),
                new AppProperties.Mail("mock", "no-reply@test", "Test"), null);
        service = new RegistrationService(accounts, new PasswordPolicyValidator(new BreachedPasswordChecker()),
                passwordHasher, termsVersionService, consentService, emailVerificationService, rateLimiter, audit,
                events, clock, properties);

        TermsVersion terms = mock(TermsVersion.class);
        when(terms.getVersion()).thenReturn(VERSION);
        TermsVersion privacy = mock(TermsVersion.class);
        when(privacy.getVersion()).thenReturn(VERSION);
        when(termsVersionService.current()).thenReturn(new TermsVersionService.CurrentTerms(terms, privacy));
        when(passwordHasher.hash(anyString())).thenReturn("hashed");
        when(accounts.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void newEmailCreatesPendingLearnerRecordsConsentAndSendsVerification() {
        when(accounts.findByEmail("lan.anh@example.com")).thenReturn(Optional.empty());

        String email = service.register(command("  Lan.Anh@Example.COM ", "song-xanh-mua-thu-2026"), CLIENT);

        assertThat(email).isEqualTo("lan.anh@example.com");
        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(accounts).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("lan.anh@example.com");
        assertThat(saved.getValue().getDisplayName()).isEqualTo("Lan Anh");
        assertThat(saved.getValue().isPendingVerification()).isTrue();
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
        verify(consentService).recordRegistrationConsents(saved.getValue().getId(), VERSION, VERSION, CLIENT);
        verify(emailVerificationService).sendInitial(saved.getValue(), CLIENT);
        verify(audit).byUser(eq(saved.getValue().getId()), eq(AuditAction.ACCOUNT_REGISTERED),
                eq(AuditObjectType.USER_ACCOUNT), eq(saved.getValue().getId()), anyMap(), eq(CLIENT));
    }

    @Test
    void existingActiveEmailNotifiesOwnerWithoutCreatingAccount() {
        UserAccount owner = UserAccount.registerLearner("lan.anh@example.com", "Lan Anh", "old", NOW);
        owner.markEmailVerified(NOW);
        when(accounts.findByEmail("lan.anh@example.com")).thenReturn(Optional.of(owner));

        String email = service.register(command("lan.anh@example.com", "song-xanh-mua-thu-2026"), CLIENT);

        assertThat(email).isEqualTo("lan.anh@example.com");
        verify(accounts, never()).save(any());
        verifyNoInteractions(consentService, emailVerificationService);
        verify(audit).byAnonymous(eq(AuditAction.REGISTRATION_ATTEMPT_EXISTING_EMAIL),
                eq(AuditObjectType.USER_ACCOUNT), eq(owner.getId()), anyMap(), eq(CLIENT));
        verify(events).publishEvent(any(AuthMailNotifier.RegistrationAttemptNoticeRequested.class));
        // Băm mật khẩu ở cả hai nhánh để thời gian phản hồi không tiết lộ địa chỉ đã đăng ký.
        verify(passwordHasher).hash("song-xanh-mua-thu-2026");
    }

    @Test
    void ownerIsNotifiedAtMostOncePerInterval() {
        UserAccount owner = UserAccount.registerLearner("lan.anh@example.com", "Lan Anh", "old", NOW);
        owner.markEmailVerified(NOW);
        when(accounts.findByEmail("lan.anh@example.com")).thenReturn(Optional.of(owner));

        service.register(command("lan.anh@example.com", "song-xanh-mua-thu-2026"), CLIENT);
        service.register(command("lan.anh@example.com", "song-xanh-mua-thu-2026"), CLIENT);

        verify(events, times(1)).publishEvent(any(AuthMailNotifier.RegistrationAttemptNoticeRequested.class));
        verify(audit, times(2)).byAnonymous(eq(AuditAction.REGISTRATION_ATTEMPT_EXISTING_EMAIL), any(), any(),
                anyMap(), any());
    }

    @Test
    void pendingEmailResendsVerificationAndKeepsOriginalPassword() {
        UserAccount pending = UserAccount.registerLearner("lan.anh@example.com", "Lan Anh", "original", NOW);
        when(accounts.findByEmail("lan.anh@example.com")).thenReturn(Optional.of(pending));

        service.register(command("lan.anh@example.com", "mot-mat-khau-khac-2026"), CLIENT);

        verify(emailVerificationService).resendIfCooledDown(pending, CLIENT, "REGISTER_AGAIN");
        verify(accounts, never()).save(any());
        assertThat(pending.getPasswordHash()).isEqualTo("original");
    }

    @Test
    void breachedPasswordIsRejectedBeforeLookingUpEmail() {
        assertThatThrownBy(() -> service.register(command("lan.anh@example.com", "Password1234"), CLIENT))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.PASSWORD_BREACHED);
        verifyNoInteractions(accounts);
    }

    @Test
    void termsMustBeAccepted() {
        RegistrationService.RegisterCommand command = new RegistrationService.RegisterCommand("Lan Anh",
                "lan.anh@example.com", "song-xanh-mua-thu-2026", false, VERSION, VERSION);

        assertThatThrownBy(() -> service.register(command, CLIENT))
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.TERMS_NOT_ACCEPTED);
        verifyNoInteractions(accounts);
    }

    @Test
    void outdatedTermsVersionIsRejected() {
        RegistrationService.RegisterCommand command = new RegistrationService.RegisterCommand("Lan Anh",
                "lan.anh@example.com", "song-xanh-mua-thu-2026", true, "2025-01-01", VERSION);

        assertThatThrownBy(() -> service.register(command, CLIENT))
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.TERMS_VERSION_OUTDATED);
    }

    @Test
    void tooManyRegistrationsFromOneAddressAreThrottled() {
        when(accounts.findByEmail(anyString())).thenReturn(Optional.empty());
        for (int i = 0; i < 3; i++) {
            service.register(command("hoc.vien" + i + "@example.com", "song-xanh-mua-thu-2026"), CLIENT);
        }

        assertThatThrownBy(() -> service.register(command("hoc.vien9@example.com", "song-xanh-mua-thu-2026"),
                CLIENT)).isInstanceOf(RateLimitExceededException.class);
    }

    private static RegistrationService.RegisterCommand command(String email, String password) {
        return new RegistrationService.RegisterCommand("  Lan   Anh ", email, password, true, VERSION, VERSION);
    }
}
