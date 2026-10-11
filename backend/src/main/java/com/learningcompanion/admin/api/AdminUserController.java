package com.learningcompanion.admin.api;

import com.learningcompanion.admin.api.dto.AdminUserSummary;
import com.learningcompanion.admin.application.AdminUserService;
import com.learningcompanion.shared.security.AuthenticatedUser;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Điểm cuối quản trị về tài khoản. Chỉ vai trò ADMIN gọi được (xem SecurityConfig); mọi tham số
 * kiểu "include=content" đều bị bỏ qua vì phản hồi không có chỗ chứa nội dung học tập.
 */
@RestController
@RequestMapping(ApiPaths.ADMIN + "/users")
public class AdminUserController {

    private final AdminUserService users;

    public AdminUserController(AdminUserService users) {
        this.users = users;
    }

    @GetMapping("/{id}")
    public AdminUserSummary get(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
                                HttpServletRequest request) {
        return users.summary(admin.userId(), id, ClientInfo.from(request));
    }
}
