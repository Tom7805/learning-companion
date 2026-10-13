package com.learningcompanion.privacy.api.dto;

import com.learningcompanion.privacy.application.AccountDeletionService;
import java.time.Instant;

/** Trạng thái xóa tài khoản; {@code scheduledFor} là lúc tài khoản bị xóa vĩnh viễn, chỉ có khi đang chờ xóa. */
public record AccountDeletionResponse(boolean pending, Instant requestedAt, Instant scheduledFor, long graceDays) {

    public static AccountDeletionResponse of(AccountDeletionService.Status status) {
        return new AccountDeletionResponse(status.pending(), status.requestedAt(), status.scheduledFor(),
                AccountDeletionService.GRACE_PERIOD.toDays());
    }
}
