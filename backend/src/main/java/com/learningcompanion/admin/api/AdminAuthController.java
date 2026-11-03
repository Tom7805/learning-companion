package com.learningcompanion.admin.api;

import com.learningcompanion.admin.api.dto.AdminIdentity;
import com.learningcompanion.admin.application.AdminUserService;
import com.learningcompanion.shared.security.AuthenticatedUser;
import com.learningcompanion.shared.web.ApiPaths;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phiên quản trị. Trang quản trị hỏi điểm cuối này trước khi hiện gì: người không phải quản trị viên nhận 403 và lần
 * từ chối được ghi nhật ký (NCL-03-CN-001 TC-05), nên mở đường dẫn trực tiếp cũng không lọt qua.
 */
@RestController
@RequestMapping(ApiPaths.ADMIN)
public class AdminAuthController {

    private final AdminUserService users;

    public AdminAuthController(AdminUserService users) {
        this.users = users;
    }

    @GetMapping("/me")
    public AdminIdentity me(@AuthenticationPrincipal AuthenticatedUser admin) {
        return users.identity(admin.userId(), admin.sessionId());
    }
}
