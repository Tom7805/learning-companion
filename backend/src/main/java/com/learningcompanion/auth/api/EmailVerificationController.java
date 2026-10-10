package com.learningcompanion.auth.api;

import com.learningcompanion.auth.api.dto.AccountResponse;
import com.learningcompanion.auth.api.dto.ResendAcceptedResponse;
import com.learningcompanion.auth.api.dto.ResendVerificationRequest;
import com.learningcompanion.auth.api.dto.VerifyEmailRequest;
import com.learningcompanion.auth.application.EmailVerificationService;
import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.AUTH + "/verify-email")
public class EmailVerificationController {

    private final EmailVerificationService verificationService;
    private final SessionCookies sessionCookies;
    private final AppProperties properties;

    public EmailVerificationController(EmailVerificationService verificationService, SessionCookies sessionCookies,
                                       AppProperties properties) {
        this.verificationService = verificationService;
        this.sessionCookies = sessionCookies;
        this.properties = properties;
    }

    /** Kích hoạt tài khoản rồi đăng nhập luôn bằng cookie phiên. */
    @PostMapping
    public ResponseEntity<AccountResponse> verify(@Valid @RequestBody VerifyEmailRequest request,
                                                  HttpServletRequest http) {
        EmailVerificationService.VerificationResult result =
                verificationService.verify(request.token(), ClientInfo.from(http));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookies.issue(result.session().rawToken()).toString())
                .body(AccountResponse.of(result.account()));
    }

    @PostMapping("/resend")
    public ResponseEntity<ResendAcceptedResponse> resend(@Valid @RequestBody ResendVerificationRequest request,
                                                         HttpServletRequest http) {
        ClientInfo client = ClientInfo.from(http);
        if (request.token() != null && !request.token().isBlank()) {
            verificationService.resendByToken(request.token(), client);
        } else {
            verificationService.resendByEmail(request.email(), client);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ResendAcceptedResponse(properties.auth().resendCooldown().toSeconds()));
    }
}
