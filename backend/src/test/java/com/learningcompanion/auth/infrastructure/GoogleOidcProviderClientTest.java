package com.learningcompanion.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Bản kết nối Google thật, kiểm với máy chủ giả: chỉ xin quyền tối thiểu, đổi mã có PKCE, đọc thông tin người dùng. */
class GoogleOidcProviderClientTest {

    private final AppProperties properties = new AppProperties("https://app.example", null, null, null, null,
            new AppProperties.OAuth("google", "client-123", "secret-456", Duration.ofMinutes(10)));

    @Test
    void authorizationUrlAsksOnlyForNameAndEmailWithPkce() {
        GoogleOidcProviderClient client = new GoogleOidcProviderClient(properties, RestClient.create());

        String url = client.authorizationUrl("trang-thai", "thu-thach", "https://app.example/auth/callback/google");

        assertThat(url).startsWith("https://accounts.google.com/o/oauth2/v2/auth?")
                .contains("client_id=client-123")
                .contains("scope=openid+email+profile")
                .contains("state=trang-thai")
                .contains("code_challenge=thu-thach")
                .contains("code_challenge_method=S256")
                .doesNotContain("secret-456");
    }

    @Test
    void exchangeSendsVerifierAndReadsUserInfo() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(GoogleOidcProviderClient.TOKEN_ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(java.util.Map.of("code", "ma-uy-quyen",
                        "code_verifier", "ma-xac-minh", "grant_type", "authorization_code")))
                .andRespond(withSuccess("{\"access_token\":\"tok\",\"token_type\":\"Bearer\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(GoogleOidcProviderClient.USERINFO_ENDPOINT))
                .andExpect(header("Authorization", "Bearer tok"))
                .andRespond(withSuccess("{\"sub\":\"1077\",\"email\":\"lan@gmail.com\",\"email_verified\":true,"
                        + "\"name\":\"Lan Anh\"}", MediaType.APPLICATION_JSON));
        GoogleOidcProviderClient client = new GoogleOidcProviderClient(properties, builder.build());

        OidcProviderClient.ExternalProfile profile = client.exchange("ma-uy-quyen", "ma-xac-minh",
                "https://app.example/auth/callback/google");

        assertThat(profile).isEqualTo(new OidcProviderClient.ExternalProfile("1077", "lan@gmail.com", true, "Lan Anh"));
        server.verify();
    }

    @Test
    void providerFailureBecomesFriendlyError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(GoogleOidcProviderClient.TOKEN_ENDPOINT)).andRespond(withServerError());
        GoogleOidcProviderClient client = new GoogleOidcProviderClient(properties, builder.build());

        assertThatThrownBy(() -> client.exchange("x", "y", "https://app.example/auth/callback/google"))
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(ErrorCode.OAUTH_PROVIDER_ERROR));
    }

    @Test
    void missingCredentialsStopStartup() {
        AppProperties incomplete = new AppProperties("https://app.example", null, null, null, null,
                new AppProperties.OAuth("google", "", null, Duration.ofMinutes(10)));
        assertThatThrownBy(() -> new GoogleOidcProviderClient(incomplete, RestClient.create()))
                .hasMessageContaining("google-client-id");
    }
}
