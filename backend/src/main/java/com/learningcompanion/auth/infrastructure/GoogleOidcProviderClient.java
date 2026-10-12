package com.learningcompanion.auth.infrastructure;

import com.learningcompanion.auth.domain.IdentityProvider;
import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Đăng nhập bằng Google theo OpenID Connect: luồng mã ủy quyền có PKCE, đổi mã ở máy chủ bằng mã bí mật
 * của ứng dụng rồi đọc thông tin người dùng qua kênh TLS tới Google. Chỉ xin quyền "openid email profile".
 */
@Component
@ConditionalOnProperty(name = "app.oauth.mode", havingValue = "google")
public class GoogleOidcProviderClient implements OidcProviderClient {

    static final String AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    static final String USERINFO_ENDPOINT = "https://openidconnect.googleapis.com/v1/userinfo";

    private final AppProperties.OAuth settings;
    private final RestClient http;

    @Autowired
    public GoogleOidcProviderClient(AppProperties properties) {
        this(properties, RestClient.create());
    }

    GoogleOidcProviderClient(AppProperties properties, RestClient http) {
        this.settings = properties.oauth();
        if (settings.googleClientId() == null || settings.googleClientId().isBlank()
                || settings.googleClientSecret() == null || settings.googleClientSecret().isBlank()) {
            throw new IllegalStateException("Thiếu app.oauth.google-client-id hoặc google-client-secret");
        }
        this.http = http;
    }

    @Override
    public IdentityProvider provider() {
        return IdentityProvider.GOOGLE;
    }

    @Override
    public String authorizationUrl(String state, String codeChallenge, String redirectUri) {
        return AUTHORIZATION_ENDPOINT + "?response_type=code"
                + "&client_id=" + encode(settings.googleClientId())
                + "&scope=" + encode(SCOPES)
                + "&redirect_uri=" + encode(redirectUri)
                + "&state=" + encode(state)
                + "&code_challenge=" + encode(codeChallenge)
                + "&code_challenge_method=S256"
                + "&prompt=select_account";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ExternalProfile exchange(String code, String codeVerifier, String redirectUri) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "authorization_code");
            form.add("code", code);
            form.add("code_verifier", codeVerifier);
            form.add("redirect_uri", redirectUri);
            form.add("client_id", settings.googleClientId());
            form.add("client_secret", settings.googleClientSecret());
            Map<String, Object> token = http.post().uri(TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            Object accessToken = token == null ? null : token.get("access_token");
            if (!(accessToken instanceof String bearer)) {
                throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
            }
            Map<String, Object> user = http.get().uri(USERINFO_ENDPOINT)
                    .header("Authorization", "Bearer " + bearer)
                    .retrieve()
                    .body(Map.class);
            if (user == null || !(user.get("sub") instanceof String subject) || !(user.get("email") instanceof String email)) {
                throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
            }
            return new ExternalProfile(subject, email, Boolean.TRUE.equals(user.get("email_verified")),
                    user.get("name") instanceof String name ? name : null);
        } catch (RestClientException ex) {
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
