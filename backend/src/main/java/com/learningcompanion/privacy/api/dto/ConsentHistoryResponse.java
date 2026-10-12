package com.learningcompanion.privacy.api.dto;

import com.learningcompanion.privacy.domain.ConsentHistory;
import java.time.Instant;
import java.util.UUID;

public record ConsentHistoryResponse(UUID id, String purpose, boolean granted, String documentVersion,
                                     Instant recordedAt) {

    public static ConsentHistoryResponse of(ConsentHistory entry) {
        return new ConsentHistoryResponse(entry.getId(), entry.getPurpose().name(), entry.isGranted(),
                entry.getDocumentVersion(), entry.getRecordedAt());
    }
}
