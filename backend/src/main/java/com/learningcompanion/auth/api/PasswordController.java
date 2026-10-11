package com.learningcompanion.auth.api;

import com.learningcompanion.auth.api.dto.ChangePasswordRequest;
import com.learningcompanion.auth.api.dto.ForgotPasswordRequest;
import com.learningcompanion.auth.api.dto.PasswordChangedResponse;
import com.learningcompanion.auth.api.dto.PasswordResetResponse;
import com.learningcompanion.auth.api.dto.ResendAcceptedResponse;
import com.learningcompanion.auth.api.dto.ResetPasswordRequest;
import com.learningcompanion.auth.api.dto.ResetTokenRequest;
import com.learningcompanion.auth.application.PasswordService;
import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.security.AuthenticatedUser;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Quên mật khẩu, đặt lại và đổi mật khẩu (NCL-01-CN-003). */
@RestController
@RequestMapping(ApiPaths.AUTH + "/password")
public class PasswordController {

    private final PasswordService passwordService;
    private final AppProperties properties;

    public PasswordController(PasswordService passwordService, AppProperties properties) {
        this.passwordService = passwordService;
        this.properties = properties;
    }

    /** Luôn trả 202 với cùng nội dung, dù địa chỉ có tài khoản hay không. */
    @PostMapping("/forgot")
    public ResponseEntity<ResendAcceptedResponse> forgot(@Valid @RequestBody ForgotPasswordRequest request,
                                                         HttpServletRequest http) {
        ClientInfo client = ClientInfo.from(http);
        if (request.token() != null && !request.token().isBlank()) {
            passwordService.requestResetByToken(request.token(), client);
        } else {
            passwordService.requestReset(request.email(), client);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ResendAcceptedResponse(properties.auth().resendCooldown().toSeconds()));
    }

    /** Kiểm đường dẫn khi mở trang đặt lại: 204 nếu dùng được, 410 nếu hết hạn, 400 nếu không hợp lệ. */
    @PostMapping("/reset/check")
    public ResponseEntity<Void> check(@Valid @RequestBody ResetTokenRequest request, HttpServletRequest http) {
        passwordService.checkResetToken(request.token(), ClientInfo.from(http));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset")
    public PasswordResetResponse reset(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        return new PasswordResetResponse(
                passwordService.resetPassword(request.token(), request.password(), ClientInfo.from(http)));
    }

    @PostMapping("/change")
    public PasswordChangedResponse change(@AuthenticationPrincipal AuthenticatedUser user,
                                          @Valid @RequestBody ChangePasswordRequest request, HttpServletRequest http) {
        return new PasswordChangedResponse(passwordService.changePassword(user, request.currentPassword(),
                request.newPassword(), ClientInfo.from(http)));
    }
}
