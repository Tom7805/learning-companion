package com.learningcompanion.auth.api.dto;

import com.learningcompanion.auth.domain.UserAccount;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(UUID id, String email, String displayName, String role, String status,
                              Instant emailVerifiedAt) {

    public static AccountResponse of(UserAccount account) {
        return new AccountResponse(account.getId(), account.getEmail(), account.getDisplayName(),
                account.getRole().name(), account.getStatus().name(), account.getEmailVerifiedAt());
    }
}
