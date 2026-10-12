package com.learningcompanion.privacy.api.dto;

import com.learningcompanion.privacy.application.ConsentService;
import com.learningcompanion.privacy.domain.Consent;
import java.time.Instant;

/** Trạng thái một mục đích không bắt buộc; chưa từng bật thì tắt và không có phiên bản. */
public record ConsentResponse(String purpose, boolean granted, String documentVersion, Instant updatedAt) {

    public static ConsentResponse of(ConsentService.PurposeState state) {
        return new ConsentResponse(state.purpose().name(), state.granted(),
                state.consent().map(Consent::getDocumentVersion).orElse(null),
                state.consent().map(Consent::getUpdatedAt).orElse(null));
    }
}
