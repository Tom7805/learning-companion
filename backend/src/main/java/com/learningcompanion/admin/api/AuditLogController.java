package com.learningcompanion.admin.api;

import com.learningcompanion.admin.api.dto.AuditLogQuery;
import com.learningcompanion.admin.api.dto.AuditLogResponse;
import com.learningcompanion.admin.application.AuditLogQueryService;
import com.learningcompanion.shared.audit.AuditTamperGuard;
import com.learningcompanion.shared.security.AuthenticatedUser;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tra cứu nhật ký thao tác (NCL-03-CN-001). Chỉ có đường đọc: mọi yêu cầu sửa hay xóa một dòng đều bị từ chối và
 * được ghi lại (TC-03), vì nhật ký chỉ được ghi thêm (QTN-06).
 */
@RestController
@RequestMapping(ApiPaths.ADMIN + "/audit-logs")
public class AuditLogController {

    private final AuditLogQueryService logs;
    private final AuditTamperGuard tamperGuard;

    public AuditLogController(AuditLogQueryService logs, AuditTamperGuard tamperGuard) {
        this.logs = logs;
        this.tamperGuard = tamperGuard;
    }

    @GetMapping
    public AuditLogResponse search(@AuthenticationPrincipal AuthenticatedUser admin, AuditLogQuery query,
                                   HttpServletRequest request) {
        return logs.search(admin.userId(), query, ClientInfo.from(request));
    }

    @RequestMapping(value = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public void tamper(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
                       HttpServletRequest request) {
        tamperGuard.recordAttempt(admin.userId(), id, request.getMethod(), ClientInfo.from(request));
        throw tamperGuard.rejection();
    }

    /** Sửa hay xóa hàng loạt cũng bị từ chối và ghi lại như sửa xóa từng dòng. */
    @RequestMapping(method = {RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public void tamperAll(@AuthenticationPrincipal AuthenticatedUser admin, HttpServletRequest request) {
        tamperGuard.recordAttempt(admin.userId(), null, request.getMethod(), ClientInfo.from(request));
        throw tamperGuard.rejection();
    }
}
