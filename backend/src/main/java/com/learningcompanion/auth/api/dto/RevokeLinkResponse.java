package com.learningcompanion.auth.api.dto;

import com.learningcompanion.auth.application.SessionService;
import java.time.Instant;

public record RevokeLinkResponse(String browser, String operatingSystem, Instant signedInAt, boolean alreadyRevoked) {

    public static RevokeLinkResponse of(SessionService.RevokedByLink result) {
        return new RevokeLinkResponse(result.device().browser(), result.device().operatingSystem(),
                result.signedInAt(), result.alreadyRevoked());
    }
}
