package com.learningcompanion.admin.api;

import com.learningcompanion.admin.api.dto.RecoveryCodeRequest;
import com.learningcompanion.admin.api.dto.TotpSetupResponse;
import com.learningcompanion.admin.api.dto.TotpVerifyRequest;
import com.learningcompanion.admin.api.dto.TwoFactorChallengeResponse;
import com.learningcompanion.admin.api.dto.TwoFactorResultResponse;
import com.learningcompanion.admin.application.AdminTwoFactorService;
import com.learningcompanion.admin.domain.AdminLoginChallenge;
import com.learningcompanion.auth.api.SessionCookies;
import com.learningcompanion.auth.api.dto.AccountResponse;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bước thứ hai khi quản trị viên đăng nhập (NCL-03-CN-002). Chưa có phiên: trình duyệt chứng minh đã qua bước mật
 * khẩu bằng cookie HttpOnly của bước này. Hoàn tất thì cấp cookie phiên quản trị và xóa cookie bước hai.
 */
@RestController
@RequestMapping(ApiPaths.AUTH + "/admin/two-factor")
public class AdminTwoFactorController {

    private final AdminTwoFactorService twoFactor;
    private final SessionCookies cookies;

    public AdminTwoFactorController(AdminTwoFactorService twoFactor, SessionCookies cookies) {
        this.twoFactor = twoFactor;
        this.cookies = cookies;
    }

    @GetMapping
    public TwoFactorChallengeResponse status(HttpServletRequest http) {
        AdminTwoFactorService.ChallengeStatus status = twoFactor.status(challenge(http));
        return new TwoFactorChallengeResponse(status.stage().name(), status.email(), status.expiresAt());
    }

    @PostMapping("/enrollment")
    public TotpSetupResponse startEnrollment(HttpServletRequest http) {
        AdminTwoFactorService.Enrollment enrollment = twoFactor.startEnrollment(challenge(http));
        return new TotpSetupResponse(enrollment.secret(), enrollment.otpauthUri());
    }

    @PostMapping("/enrollment/confirm")
    public ResponseEntity<TwoFactorResultResponse> confirmEnrollment(@Valid @RequestBody TotpVerifyRequest request,
                                                                     HttpServletRequest http) {
        SessionCookies.DeviceCookie device = cookies.device(http);
        return signedIn(twoFactor.confirmEnrollment(challenge(http), request.code(), device.id(),
                ClientInfo.from(http)), device);
    }

    @PostMapping("/verify")
    public ResponseEntity<TwoFactorResultResponse> verify(@Valid @RequestBody TotpVerifyRequest request,
                                                          HttpServletRequest http) {
        SessionCookies.DeviceCookie device = cookies.device(http);
        AdminTwoFactorService.Outcome outcome = twoFactor.verify(challenge(http), request.code(), device.id(),
                ClientInfo.from(http));
        return outcome.isEnrollmentRequired() ? enrollmentRequired() : signedIn(outcome.completed(), device);
    }

    /** Mất điện thoại sinh mã: dùng một mã khôi phục, sau đó phải thiết lập lại ngay (TC-03). */
    @PostMapping("/recover")
    public ResponseEntity<TwoFactorResultResponse> recover(@Valid @RequestBody RecoveryCodeRequest request,
                                                           HttpServletRequest http) {
        twoFactor.recover(challenge(http), request.recoveryCode(), ClientInfo.from(http));
        return enrollmentRequired();
    }

    private String challenge(HttpServletRequest http) {
        return cookies.adminChallenge(http).orElse(null);
    }

    private static ResponseEntity<TwoFactorResultResponse> enrollmentRequired() {
        return ResponseEntity.ok(new TwoFactorResultResponse(false, AdminLoginChallenge.Stage.ENROLL.name(), null,
                null, List.of()));
    }

    private ResponseEntity<TwoFactorResultResponse> signedIn(AdminTwoFactorService.Completed completed,
                                                             SessionCookies.DeviceCookie device) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.issue(completed.session().rawToken(), false,
                        completed.session().lifetime()).toString())
                .header(HttpHeaders.SET_COOKIE, device.cookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookies.clearAdminChallenge().toString())
                .body(new TwoFactorResultResponse(true, null, AccountResponse.of(completed.account()),
                        completed.sessionExpiresAt(), completed.recoveryCodes()));
    }
}
