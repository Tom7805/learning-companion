package com.learningcompanion.auth.api;

import com.learningcompanion.auth.api.dto.AccountResponse;
import com.learningcompanion.auth.api.dto.RegisterRequest;
import com.learningcompanion.auth.api.dto.RegistrationAcceptedResponse;
import com.learningcompanion.auth.api.dto.SessionStatusResponse;
import com.learningcompanion.auth.application.RegistrationService;
import com.learningcompanion.auth.application.SessionService;
import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.security.AuthenticatedUser;
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
    private final SessionService sessionService;
    private final SessionCookies sessionCookies;
    private final AppProperties properties;

    public AuthController(RegistrationService registrationService, SessionService sessionService,
                          SessionCookies sessionCookies, AppProperties properties) {
        this.registrationService = registrationService;
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

    @GetMapping("/session")
    public SessionStatusResponse session(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) {
            return SessionStatusResponse.anonymous();
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
}
