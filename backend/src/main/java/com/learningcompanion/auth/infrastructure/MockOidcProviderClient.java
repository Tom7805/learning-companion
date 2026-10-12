package com.learningcompanion.auth.infrastructure;

import com.learningcompanion.auth.domain.IdentityProvider;
import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Nhà cung cấp giả lập cho máy phát triển và kiểm thử: trang cho phép quyền nằm ở giao diện
 * ({@code /dev/oauth/authorize}), mã ủy quyền giữ trong bộ nhớ, dùng một lần và kiểm PKCE như nhà cung cấp thật.
 * Không bao giờ được bật ở môi trường thật.
 */
@Component
@ConditionalOnProperty(name = "app.oauth.mode", havingValue = "mock")
public class MockOidcProviderClient implements OidcProviderClient {

    private static final Duration CODE_TTL = Duration.ofMinutes(2);

    private final Map<String, IssuedCode> codes = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final AppProperties properties;
    private final Clock clock;

    public MockOidcProviderClient(AppProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public IdentityProvider provider() {
        return IdentityProvider.GOOGLE;
    }

    @Override
    public String authorizationUrl(String state, String codeChallenge, String redirectUri) {
        return properties.frontendUrl() + "/dev/oauth/authorize?response_type=code&client_id=mock"
                + "&scope=" + encode(SCOPES)
                + "&redirect_uri=" + encode(redirectUri)
                + "&state=" + encode(state)
                + "&code_challenge=" + encode(codeChallenge)
                + "&code_challenge_method=S256";
    }

    /** Trang cho phép quyền giả lập gọi khi người thử bấm "Cho phép". */
    public String issueCode(ExternalProfile profile, String codeChallenge, String redirectUri) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        codes.put(code, new IssuedCode(profile, codeChallenge, redirectUri, clock.instant().plus(CODE_TTL)));
        return code;
    }

    @Override
    public ExternalProfile exchange(String code, String codeVerifier, String redirectUri) {
        IssuedCode issued = code == null ? null : codes.remove(code);
        if (issued == null || !clock.instant().isBefore(issued.expiresAt())
                || !issued.redirectUri().equals(redirectUri)
                || !issued.codeChallenge().equals(Pkce.challengeOf(codeVerifier))) {
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
        return issued.profile();
    }

    /** Mã định danh ổn định theo địa chỉ thư, giống "sub" của nhà cung cấp thật. */
    public static String subjectFor(String email) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(email.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            return "mock-" + HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record IssuedCode(ExternalProfile profile, String codeChallenge, String redirectUri, Instant expiresAt) {
    }
}
