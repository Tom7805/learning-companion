package com.learningcompanion.privacy.api;

import com.learningcompanion.privacy.api.dto.AcceptTermsRequest;
import com.learningcompanion.privacy.api.dto.ConsentHistoryResponse;
import com.learningcompanion.privacy.api.dto.PrivacyStatusResponse;
import com.learningcompanion.privacy.api.dto.UpdateConsentRequest;
import com.learningcompanion.privacy.application.ConsentService;
import com.learningcompanion.privacy.domain.ConsentPurpose;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Đồng ý theo mục đích của một tài khoản; chỉ chính chủ tài khoản thao tác được (TC-04). */
@RestController
@RequestMapping(ApiPaths.ACCOUNTS + "/{accountId}")
public class ConsentController {

    private final ConsentService consents;

    public ConsentController(ConsentService consents) {
        this.consents = consents;
    }

    @GetMapping("/privacy")
    public PrivacyStatusResponse status(@PathVariable UUID accountId, HttpServletRequest http) {
        return PrivacyStatusResponse.of(consents.status(accountId, ClientInfo.from(http)));
    }

    @PutMapping("/consents/{purpose}")
    public PrivacyStatusResponse update(@PathVariable UUID accountId, @PathVariable ConsentPurpose purpose,
                                        @Valid @RequestBody UpdateConsentRequest request, HttpServletRequest http) {
        return PrivacyStatusResponse.of(
                consents.setPurpose(accountId, purpose, request.granted(), ClientInfo.from(http)));
    }

    @GetMapping("/consents/history")
    public List<ConsentHistoryResponse> history(@PathVariable UUID accountId, HttpServletRequest http) {
        return consents.history(accountId, ClientInfo.from(http)).stream().map(ConsentHistoryResponse::of).toList();
    }

    @PostMapping("/terms/accept")
    public PrivacyStatusResponse accept(@PathVariable UUID accountId, @Valid @RequestBody AcceptTermsRequest request,
                                        HttpServletRequest http) {
        return PrivacyStatusResponse.of(consents.acceptCurrentTerms(accountId, request.termsVersion(),
                request.privacyVersion(), ClientInfo.from(http)));
    }
}
