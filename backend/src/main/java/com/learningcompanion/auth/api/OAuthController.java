package com.learningcompanion.auth.api;

import com.learningcompanion.auth.api.dto.AccountResponse;
import com.learningcompanion.auth.application.OAuthLoginService;
import com.learningcompanion.auth.domain.OAuthFlow;
import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.security.AuthenticatedUser;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Đăng nhập qua nhà cung cấp định danh (NCL-01-CN-007). Trình duyệt bắt đầu lượt nào thì giữ cookie riêng
 * của lượt đó; kết quả trả về từ trình duyệt khác bị từ chối như mã chống giả mạo sai.
 */
@RestController
@RequestMapping(ApiPaths.AUTH + "/oauth")
public class OAuthController {

    static final String BROWSER_COOKIE = "lc_oauth";
    private static final String COOKIE_PATH = ApiPaths.AUTH + "/oauth";

    private final OAuthLoginService oauth;
    private final SessionCookies sessionCookies;
    private final AppProperties properties;

    public OAuthController(OAuthLoginService oauth, SessionCookies sessionCookies, AppProperties properties) {
        this.oauth = oauth;
        this.sessionCookies = sessionCookies;
        this.properties = properties;
    }

    @GetMapping("/providers")
    public List<ProviderResponse> providers() {
        return oauth.enabledProviders().stream()
                .map(provider -> new ProviderResponse(provider.id(), provider.displayName()))
                .toList();
    }

    @PostMapping("/{provider}/start")
    public ResponseEntity<StartResponse> start(@PathVariable String provider, @RequestBody StartRequest request,
                                               @AuthenticationPrincipal AuthenticatedUser user,
                                               HttpServletRequest http) {
        OAuthFlow.Mode mode = "LINK".equalsIgnoreCase(request.mode()) ? OAuthFlow.Mode.LINK : OAuthFlow.Mode.LOGIN;
        OAuthLoginService.Started started = oauth.start(provider, mode, Boolean.TRUE.equals(request.rememberDevice()),
                request.returnTo(), user == null ? null : user.userId(), ClientInfo.from(http));
        ResponseCookie cookie = ResponseCookie.from(BROWSER_COOKIE, started.browserToken())
                .httpOnly(true)
                .secure(properties.auth().secureCookies())
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(properties.oauth().flowTtl())
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new StartResponse(started.authorizationUrl()));
    }

    @PostMapping("/{provider}/callback")
    public ResponseEntity<ResultResponse> callback(@PathVariable String provider,
                                                   @RequestBody CallbackRequest request,
                                                   @AuthenticationPrincipal AuthenticatedUser user,
                                                   HttpServletRequest http) {
        SessionCookies.DeviceCookie device = sessionCookies.device(http);
        UUID currentUser = user == null ? null : user.userId();
        OAuthLoginService.Result result = oauth.callback(provider, request.code(), request.state(), request.error(),
                browserToken(http).orElse(null), currentUser, device.id(), ClientInfo.from(http));
        return respond(result, device);
    }

    @PostMapping("/signup")
    public ResponseEntity<ResultResponse> signup(@Valid @RequestBody SignupRequest request, HttpServletRequest http) {
        SessionCookies.DeviceCookie device = sessionCookies.device(http);
        return respond(oauth.completeSignup(request.token(), request.displayName(),
                Boolean.TRUE.equals(request.acceptTerms()), request.termsVersion(), request.privacyVersion(),
                device.id(), ClientInfo.from(http)), device);
    }

    @PostMapping("/link")
    public ResponseEntity<ResultResponse> link(@Valid @RequestBody LinkRequest request, HttpServletRequest http) {
        SessionCookies.DeviceCookie device = sessionCookies.device(http);
        return respond(oauth.confirmLink(request.token(), request.password(), device.id(), ClientInfo.from(http)),
                device);
    }

    private ResponseEntity<ResultResponse> respond(OAuthLoginService.Result result, SessionCookies.DeviceCookie device) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, ResponseCookie.from(BROWSER_COOKIE, "")
                        .httpOnly(true).secure(properties.auth().secureCookies()).sameSite("Lax")
                        .path(COOKIE_PATH).maxAge(0).build().toString());
        if (result.session() != null) {
            response.header(HttpHeaders.SET_COOKIE, sessionCookies.issue(result.session().rawToken(),
                            result.session().rememberDevice(), result.session().lifetime()).toString())
                    .header(HttpHeaders.SET_COOKIE, device.cookie().toString());
        }
        return response.body(new ResultResponse(result.status().name(), result.provider().id(),
                result.account() == null ? null : AccountResponse.of(result.account()), result.pendingToken(),
                result.email(), result.displayName(), result.returnTo()));
    }

    private static Optional<String> browserToken(HttpServletRequest http) {
        return Optional.ofNullable(http.getCookies()).flatMap(cookies -> Arrays.stream(cookies)
                .filter(cookie -> BROWSER_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst());
    }

    public record ProviderResponse(String id, String name) {
    }

    public record StartRequest(String mode, Boolean rememberDevice, String returnTo) {
    }

    public record StartResponse(String authorizationUrl) {
    }

    public record CallbackRequest(String code, String state, String error) {
    }

    public record SignupRequest(@NotBlank String token, String displayName, Boolean acceptTerms,
                                String termsVersion, String privacyVersion) {
    }

    public record LinkRequest(@NotBlank String token, String password) {
    }

    /**
     * Kết quả: SIGNED_IN (kèm tài khoản, đã đặt cookie phiên), LINKED, TERMS_REQUIRED hoặc LINK_REQUIRED
     * (kèm mã tiếp tục dùng cho bước sau, hết hạn sau mười lăm phút).
     */
    public record ResultResponse(String status, String provider, AccountResponse account, String pendingToken,
                                 String email, String displayName, String returnTo) {
    }
}
