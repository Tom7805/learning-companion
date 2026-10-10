package com.learningcompanion.auth.api;

import com.learningcompanion.auth.api.dto.RevokeLinkRequest;
import com.learningcompanion.auth.api.dto.RevokeLinkResponse;
import com.learningcompanion.auth.api.dto.RevokeOthersResponse;
import com.learningcompanion.auth.api.dto.SessionResponse;
import com.learningcompanion.auth.application.SessionService;
import com.learningcompanion.shared.security.AuthenticatedUser;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Danh sách thiết bị đang đăng nhập và đăng xuất từ xa (NCL-01-CN-002). */
@RestController
@RequestMapping(ApiPaths.AUTH + "/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final SessionCookies sessionCookies;

    public SessionController(SessionService sessionService, SessionCookies sessionCookies) {
        this.sessionService = sessionService;
        this.sessionCookies = sessionCookies;
    }

    @GetMapping
    public List<SessionResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return sessionService.activeSessions(user.userId()).stream()
                .map(session -> SessionResponse.of(session, user.sessionId()))
                .toList();
    }

    /** Đăng xuất một thiết bị; nếu là thiết bị đang dùng thì xóa luôn cookie phiên. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                       HttpServletRequest http) {
        sessionService.revoke(user, id, ClientInfo.from(http));
        ResponseEntity.HeadersBuilder<?> response = ResponseEntity.noContent();
        if (id.equals(user.sessionId())) {
            response.header(HttpHeaders.SET_COOKIE, sessionCookies.clear().toString());
        }
        return response.build();
    }

    @PostMapping("/revoke-others")
    public RevokeOthersResponse revokeOthers(@AuthenticationPrincipal AuthenticatedUser user,
                                             HttpServletRequest http) {
        return new RevokeOthersResponse(sessionService.revokeOthers(user, ClientInfo.from(http)));
    }

    /** Nút "đăng xuất thiết bị đó" trong thư cảnh báo, không cần đăng nhập. */
    @PostMapping("/revoke-link")
    public RevokeLinkResponse revokeByLink(@Valid @RequestBody RevokeLinkRequest request, HttpServletRequest http) {
        return RevokeLinkResponse.of(sessionService.revokeByLink(request.token(), ClientInfo.from(http)));
    }
}
