package com.learningcompanion.auth.api;

import com.learningcompanion.auth.application.AccountLinkingService;
import com.learningcompanion.auth.domain.ExternalIdentity;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tài khoản bên ngoài đã liên kết của chính người học; mã tài khoản của người khác trả 404. */
@RestController
@RequestMapping(ApiPaths.ACCOUNTS + "/{accountId}/linked-identities")
public class AccountLinkController {

    private final AccountLinkingService linking;

    public AccountLinkController(AccountLinkingService linking) {
        this.linking = linking;
    }

    @GetMapping
    public List<LinkedIdentityResponse> list(@PathVariable UUID accountId, HttpServletRequest http) {
        return linking.list(accountId, ClientInfo.from(http)).stream().map(LinkedIdentityResponse::of).toList();
    }

    @DeleteMapping("/{identityId}")
    public ResponseEntity<Void> unlink(@PathVariable UUID accountId, @PathVariable UUID identityId,
                                       HttpServletRequest http) {
        linking.unlink(accountId, identityId, ClientInfo.from(http));
        return ResponseEntity.noContent().build();
    }

    public record LinkedIdentityResponse(UUID id, String provider, String providerName, String email,
                                         Instant linkedAt, Instant lastUsedAt) {

        static LinkedIdentityResponse of(ExternalIdentity identity) {
            return new LinkedIdentityResponse(identity.getId(), identity.getProvider().id(),
                    identity.getProvider().displayName(), identity.getEmail(), identity.getLinkedAt(),
                    identity.getLastUsedAt());
        }
    }
}
