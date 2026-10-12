package com.learningcompanion.privacy.api.dto;

import com.learningcompanion.privacy.application.ConsentService;
import java.util.List;
import java.util.UUID;

/**
 * Trang quyền riêng tư của chính người học: văn bản đang áp dụng, phiên bản người học đã đồng ý,
 * có cần đồng ý lại hay không và trạng thái từng mục đích không bắt buộc.
 */
public record PrivacyStatusResponse(UUID accountId, TermsVersionResponse.Document terms,
                                    TermsVersionResponse.Document privacy, String acceptedTermsVersion,
                                    String acceptedPrivacyVersion, boolean acceptanceRequired,
                                    List<ConsentResponse> purposes) {

    public static PrivacyStatusResponse of(ConsentService.PrivacyStatus status) {
        return new PrivacyStatusResponse(status.accountId(),
                TermsVersionResponse.Document.of(status.current().terms()),
                TermsVersionResponse.Document.of(status.current().privacy()),
                status.acceptedTermsVersion(), status.acceptedPrivacyVersion(), status.acceptanceRequired(),
                status.purposes().stream().map(ConsentResponse::of).toList());
    }
}
