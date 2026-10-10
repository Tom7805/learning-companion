package com.learningcompanion.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.learningcompanion.auth.application.AuthMailNotifier;
import com.learningcompanion.auth.application.AuthenticationService;
import com.learningcompanion.auth.application.SessionService;
import com.learningcompanion.auth.domain.LoginThrottle;
import com.learningcompanion.auth.domain.LoginThrottleRepository;
import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.security.PasswordHasher;
import com.learningcompanion.shared.security.RateLimiter;
import com.learningcompanion.shared.time.AdjustableClock;
import com.learningcompanion.shared.web.ClientInfo;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class AuthenticationServiceTest {

    private static final ClientInfo CLIENT = new ClientInfo("203.0.113.7", "JUnit");
    private static final String EMAIL = "lan.anh@example.com";
    private static final String PASSWORD = "song-xanh-mua-thu-2026";

    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final LoginThrottleRepository throttles = mock(LoginThrottleRepository.class);
    private final PasswordHasher passwordHasher = mock(PasswordHasher.class);
    private final SessionService sessionService = mock(SessionService.class);
    private final AuditLogger audit = mock(AuditLogger.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AdjustableClock clock = new AdjustableClock(Clock.fixed(Instant.parse("2026-10-10T03:00:00Z"),
            ZoneOffset.UTC));
    private final Map<String, LoginThrottle> throttleStore = new HashMap<>();
    private AuthenticationService service;
    private UserAccount account;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties("http://localhost:5173",
                new AppProperties.Auth(Duration.ofHours(24), Duration.ofSeconds(60), Duration.ofHours(2),
                        "lc_session", false, 10, 20, Duration.ofHours(1), Duration.ofDays(30), 5,
                        Duration.ofMinutes(15), 1000, "lc_device"),
                new AppProperties.Mail("mock", "no-reply@test", "Test"), null);
        when(passwordHasher.hash(anyString())).thenReturn("dummy-hash");
        when(passwordHasher.matches(anyString(), anyString())).thenReturn(false);
        when(passwordHasher.matches(PASSWORD, "real-hash")).thenReturn(true);
        when(throttles.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(throttleStore.get(inv.<String>getArgument(0))));
        when(throttles.save(any(LoginThrottle.class))).thenAnswer(inv -> {
            LoginThrottle throttle = inv.getArgument(0);
            throttleStore.put(throttle.getEmail(), throttle);
            return throttle;
        });
        org.mockito.Mockito.doAnswer(inv -> throttleStore.remove(inv.<LoginThrottle>getArgument(0).getEmail()))
                .when(throttles).delete(any(LoginThrottle.class));

        account = UserAccount.registerLearner(EMAIL, "Lan Anh", "real-hash", clock.instant());
        account.markEmailVerified(clock.instant());
        when(accounts.findByEmail(EMAIL)).thenReturn(Optional.of(account));
        when(accounts.findByEmail("chua.dang.ky@example.com")).thenReturn(Optional.empty());
        when(sessionService.start(any(), any(), anyBoolean(), any(), anyString())).thenAnswer(inv ->
                new SessionService.StartedSession(UUID.randomUUID(), "raw", inv.getArgument(2),
                        inv.<Boolean>getArgument(2) ? Duration.ofDays(30) : Duration.ofHours(2)));

        service = new AuthenticationService(accounts, throttles, passwordHasher, sessionService,
                new RateLimiter(clock), audit, events, clock, properties);
    }

    @Test
    void correctPasswordStartsRememberedSessionAndClearsFailures() {
        fail(EMAIL, 2);

        AuthenticationService.LoginResult result = service.login(command(EMAIL, PASSWORD, true), CLIENT);

        assertThat(result.account()).isSameAs(account);
        assertThat(result.session().rememberDevice()).isTrue();
        assertThat(result.session().lifetime()).isEqualTo(Duration.ofDays(30));
        verify(sessionService).start(account, CLIENT, true, "device-1", "LOGIN");
        assertThat(throttleStore).doesNotContainKey(EMAIL);
    }

    @Test
    void wrongPasswordReportsRemainingAttempts() {
        assertThatThrownBy(() -> service.login(command(EMAIL, "sai-mat-khau", false), CLIENT))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException business = (BusinessException) ex;
                    assertThat(business.code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
                    assertThat(business.details()).containsEntry("remainingAttempts", 4);
                });
    }

    @Test
    void fifthFailureLocksForFifteenMinutesAndNotifiesOwner() {
        fail(EMAIL, 4);

        assertThatThrownBy(() -> service.login(command(EMAIL, "sai-mat-khau", false), CLIENT))
                .satisfies(ex -> {
                    BusinessException business = (BusinessException) ex;
                    assertThat(business.code()).isEqualTo(ErrorCode.ACCOUNT_LOCKED);
                    assertThat(business.details()).containsEntry("retryAfterSeconds", 900L);
                });
        verify(events).publishEvent(any(AuthMailNotifier.AccountLockedNotice.class));
    }

    @Test
    void sixthAttemptIsRejectedEvenWithCorrectPasswordWhileLocked() {
        fail(EMAIL, 5);
        clock.advance(Duration.ofMinutes(5));

        assertThatThrownBy(() -> service.login(command(EMAIL, PASSWORD, false), CLIENT))
                .satisfies(ex -> {
                    BusinessException business = (BusinessException) ex;
                    assertThat(business.code()).isEqualTo(ErrorCode.ACCOUNT_LOCKED);
                    assertThat(business.details()).containsEntry("retryAfterSeconds", 600L);
                });
        verify(sessionService, never()).start(any(), any(), anyBoolean(), any(), anyString());
    }

    @Test
    void lockExpiresAfterFifteenMinutes() {
        fail(EMAIL, 5);
        clock.advance(Duration.ofMinutes(15));

        assertThat(service.login(command(EMAIL, PASSWORD, false), CLIENT).account()).isSameAs(account);
    }

    @Test
    void unknownEmailIsCountedAndLockedTheSameWayWithoutMail() {
        String unknown = "chua.dang.ky@example.com";
        fail(unknown, 4);

        assertThatThrownBy(() -> service.login(command(unknown, PASSWORD, false), CLIENT))
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(ErrorCode.ACCOUNT_LOCKED));
        verify(events, never()).publishEvent(any(AuthMailNotifier.AccountLockedNotice.class));
        // Vẫn kiểm mật khẩu với mã băm giả để thời gian phản hồi như tài khoản thật.
        verify(passwordHasher, org.mockito.Mockito.atLeastOnce()).matches(PASSWORD, "dummy-hash");
    }

    @Test
    void unverifiedAccountWithCorrectPasswordIsAskedToVerify() {
        UserAccount pending = UserAccount.registerLearner("cho@example.com", "Chờ", "real-hash", clock.instant());
        when(accounts.findByEmail("cho@example.com")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.login(command("cho@example.com", PASSWORD, false), CLIENT))
                .satisfies(ex -> assertThat(((BusinessException) ex).code())
                        .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED));
    }

    @Test
    void emailIsNormalizedBeforeLookup() {
        service.login(command("  LAN.Anh@Example.com ", PASSWORD, false), CLIENT);
        verify(sessionService).start(eq(account), eq(CLIENT), eq(false), eq("device-1"), eq("LOGIN"));
    }

    private void fail(String email, int times) {
        for (int i = 0; i < times; i++) {
            try {
                service.login(command(email, "sai-mat-khau-" + i, false), CLIENT);
            } catch (BusinessException expected) {
                // Bỏ qua: đang dựng trạng thái nhập sai.
            }
        }
    }

    private static AuthenticationService.LoginCommand command(String email, String password, boolean remember) {
        return new AuthenticationService.LoginCommand(email, password, remember, "device-1");
    }
}
