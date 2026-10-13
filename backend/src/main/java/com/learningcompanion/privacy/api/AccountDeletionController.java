package com.learningcompanion.privacy.api;

import com.learningcompanion.privacy.api.dto.AccountDeletionResponse;
import com.learningcompanion.privacy.api.dto.DeleteAccountRequest;
import com.learningcompanion.privacy.application.AccountDeletionService;
import com.learningcompanion.shared.security.AuthenticatedUser;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Xóa tài khoản có thời gian chờ (NCL-01-CN-009). Chỉ chính chủ tài khoản thao tác được; yêu cầu xóa thu hồi mọi
 * phiên, kể cả phiên đang gửi yêu cầu.
 */
@RestController
@RequestMapping(ApiPaths.ACCOUNTS + "/{accountId}/deletion")
public class AccountDeletionController {

    private final AccountDeletionService deletions;

    public AccountDeletionController(AccountDeletionService deletions) {
        this.deletions = deletions;
    }

    @GetMapping
    public AccountDeletionResponse status(@PathVariable UUID accountId, HttpServletRequest http) {
        return AccountDeletionResponse.of(deletions.status(accountId, ClientInfo.from(http)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AccountDeletionResponse request(@AuthenticationPrincipal AuthenticatedUser user,
                                           @PathVariable UUID accountId,
                                           @Valid @RequestBody DeleteAccountRequest request, HttpServletRequest http) {
        return AccountDeletionResponse.of(deletions.request(user, accountId, request.password(),
                ClientInfo.from(http)));
    }

    @DeleteMapping
    public AccountDeletionResponse cancel(@PathVariable UUID accountId, HttpServletRequest http) {
        return AccountDeletionResponse.of(deletions.cancel(accountId, ClientInfo.from(http)));
    }
}
