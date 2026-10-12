package com.learningcompanion.devtools;

import com.learningcompanion.auth.infrastructure.MockOidcProviderClient;
import com.learningcompanion.auth.infrastructure.OidcProviderClient;
import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.web.ApiPaths;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trang cho phép quyền của nhà cung cấp giả lập gọi vào đây khi người thử bấm "Cho phép" hoặc "Từ chối":
 * trả về địa chỉ để chuyển trình duyệt về ứng dụng, giống hệt nhà cung cấp thật.
 */
@RestController
@RequestMapping(ApiPaths.DEV + "/oauth")
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class DevOAuthController {

    private final ObjectProvider<MockOidcProviderClient> provider;
    private final AppProperties properties;

    public DevOAuthController(ObjectProvider<MockOidcProviderClient> provider, AppProperties properties) {
        this.provider = provider;
        this.properties = properties;
    }

    @PostMapping("/authorize")
    public Redirect authorize(@RequestBody AuthorizeRequest request) {
        MockOidcProviderClient mock = provider.getIfAvailable();
        if (mock == null) {
            throw new BusinessException(ErrorCode.OAUTH_DISABLED);
        }
        // Chỉ chuyển về chính ứng dụng, như nhà cung cấp thật chỉ chấp nhận địa chỉ chuyển hướng đã đăng ký.
        if (request.redirectUri() == null || !request.redirectUri().startsWith(properties.frontendUrl() + "/")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        String state = URLEncoder.encode(request.state() == null ? "" : request.state(), StandardCharsets.UTF_8);
        if (!request.allow()) {
            return new Redirect(request.redirectUri() + "?error=access_denied&state=" + state);
        }
        String email = request.email() == null ? "" : request.email().trim();
        String code = mock.issueCode(new OidcProviderClient.ExternalProfile(
                MockOidcProviderClient.subjectFor(email), email, request.emailVerified(), request.name()),
                request.codeChallenge(), request.redirectUri());
        return new Redirect(request.redirectUri() + "?code=" + code + "&state=" + state);
    }

    public record AuthorizeRequest(String redirectUri, String state, String codeChallenge, String email, String name,
                                   boolean emailVerified, boolean allow) {
    }

    public record Redirect(String redirectUrl) {
    }
}
