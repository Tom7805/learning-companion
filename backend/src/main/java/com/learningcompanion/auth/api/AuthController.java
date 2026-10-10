package com.learningcompanion.auth.api;

import com.learningcompanion.auth.api.dto.AccountResponse;
import com.learningcompanion.auth.api.dto.LoginRequest;
import com.learningcompanion.auth.api.dto.RegisterRequest;
import com.learningcompanion.auth.api.dto.RegistrationAcceptedResponse;
import com.learningcompanion.auth.api.dto.SessionStatusResponse;
import com.learningcompanion.auth.application.AuthenticationService;
import com.learningcompanion.auth.application.RegistrationService;
import com.learningcompanion.auth.application.SessionService;
import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.security.AuthenticatedUser;
import com.learningcompanion.shared.security.SessionAuthenticationFilter;
import com.learningcompanion.shared.security.SessionResolver;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.AUTH)
public class AuthController {

    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;
    private final SessionService sessionService;
    private final SessionCookies sessionCookies;
    private final AppProperties properties;

    public AuthController(RegistrationService registrationService, AuthenticationService authenticationService,
                          SessionService sessionService, SessionCookies sessionCookies, AppProperties properties) {
        this.registrationService = registrationService;
        this.authenticationService = authenticationService;
        this.sessionService = sessionService;
        this.sessionCookies = sessionCookies;
        this.properties = properties;
    }

    @PostMapping("/register")
    public ResponseEntity<RegistrationAcceptedResponse> register(@Valid @RequestBody RegisterRequest request,
                                                                 HttpServletRequest http) {
        String email = registrationService.register(new RegistrationService.RegisterCommand(
                request.displayName(), request.email(), request.password(), request.acceptTerms(),
                request.termsVersion(), request.privacyVersion()), ClientInfo.from(http));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new RegistrationAcceptedResponse(
                email, properties.auth().resendCooldown().toSeconds()));
    }

    @PostMapping("/login")
    public ResponseEntity<AccountResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        SessionCookies.DeviceCookie device = sessionCookies.device(http);
        AuthenticationService.LoginResult result = authenticationService.login(new AuthenticationService.LoginCommand(
                request.email(), request.password(), request.rememberDevice(), device.id()), ClientInfo.from(http));
        SessionService.StartedSession session = result.session();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookies.issue(session.rawToken(), session.rememberDevice(),
                        session.lifetime()).toString())
                .header(HttpHeaders.SET_COOKIE, device.cookie().toString())
                .body(AccountResponse.of(result.account()));
    }

    @GetMapping("/session")
    public SessionStatusResponse session(@AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest http) {
        if (user == null) {
            return new SessionStatusResponse(false, null, reason(http));
        }
        return sessionService.currentAccount(user.userId())
                .map(account -> new SessionStatusResponse(true, AccountResponse.of(account)))
                .orElseGet(SessionStatusResponse::anonymous);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest http) {
        sessionService.logout(user, ClientInfo.from(http));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookies.clear().toString())
                .build();
    }

    private static String reason(HttpServletRequest http) {
        Object problem = http.getAttribute(SessionAuthenticationFilter.SESSION_PROBLEM);
        if (problem == SessionResolver.Problem.EXPIRED) {
            return ErrorCode.SESSION_EXPIRED.name();
        }
        if (problem == SessionResolver.Problem.REVOKED) {
            return ErrorCode.SESSION_REVOKED.name();
        }
        return null;
    }
}
