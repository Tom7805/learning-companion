package com.learningcompanion.ai.quota.api;

import com.learningcompanion.ai.quota.api.dto.AdminQuotaResponse;
import com.learningcompanion.ai.quota.api.dto.QuotaPolicyRequest;
import com.learningcompanion.ai.quota.api.dto.TaskCostsRequest;
import com.learningcompanion.ai.quota.application.QuotaService;
import com.learningcompanion.shared.security.AuthenticatedUser;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hạn mức chung và bảng quy đổi lượt (NCL-04-CN-003). Nằm trong khu quản trị nên chỉ vai trò ADMIN gọi được; người học
 * gửi yêu cầu đổi thì bị từ chối và lần từ chối được ghi nhật ký (TC-05, xem SecurityConfig).
 */
@RestController
@RequestMapping(ApiPaths.ADMIN + "/quotas")
public class AdminQuotaController {

    private final QuotaService quotas;

    public AdminQuotaController(QuotaService quotas) {
        this.quotas = quotas;
    }

    @GetMapping
    public AdminQuotaResponse overview() {
        return quotas.adminOverview();
    }

    @PutMapping("/policy")
    public AdminQuotaResponse updatePolicy(@AuthenticationPrincipal AuthenticatedUser user,
                                           @Valid @RequestBody QuotaPolicyRequest body, HttpServletRequest request) {
        return quotas.updatePolicy(user.userId(), body, ClientInfo.from(request));
    }

    @PutMapping("/costs")
    public AdminQuotaResponse updateCosts(@AuthenticationPrincipal AuthenticatedUser user,
                                          @Valid @RequestBody TaskCostsRequest body, HttpServletRequest request) {
        return quotas.updateCosts(user.userId(), body, ClientInfo.from(request));
    }
}
