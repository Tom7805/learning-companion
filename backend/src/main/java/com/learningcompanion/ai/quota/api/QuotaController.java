package com.learningcompanion.ai.quota.api;

import com.learningcompanion.ai.quota.api.dto.QuotaStatusResponse;
import com.learningcompanion.ai.quota.application.QuotaService;
import com.learningcompanion.shared.security.AuthenticatedUser;
import com.learningcompanion.shared.web.ApiPaths;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Đồng hồ lượt trên khung ứng dụng của người học (NCL-04-CN-003). Chỉ đọc nên không bao giờ bị hạn mức chặn. */
@RestController
@RequestMapping(ApiPaths.QUOTA)
public class QuotaController {

    private final QuotaService quotas;

    public QuotaController(QuotaService quotas) {
        this.quotas = quotas;
    }

    @GetMapping
    public QuotaStatusResponse status(@AuthenticationPrincipal AuthenticatedUser user) {
        return quotas.status(user.userId());
    }
}
