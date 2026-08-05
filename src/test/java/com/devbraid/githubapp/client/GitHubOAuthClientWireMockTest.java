package com.devbraid.githubapp.client;

import com.devbraid.githubapp.exception.GitHubOAuthException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WireMock tests for GitHubOAuthClient — github.com (token exchange) and
 * api.github.com (user fetch) are mocked as two separate WireMock servers.
 */
@DisplayName("GitHubOAuthClient WireMock Tests")
class GitHubOAuthClientWireMockTest {

    private static final int OAUTH_PORT = 8101;
    private static final int API_PORT = 8102;
    private static final String OAUTH_BASE = "http://localhost:" + OAUTH_PORT;
    private static final String API_BASE = "http://localhost:" + API_PORT;

    private static WireMockServer oauthServer;
    private static WireMockServer apiServer;

    private GitHubOAuthClient client;

    @BeforeAll
    static void startWireMock() {
        oauthServer = new WireMockServer(OAUTH_PORT);
        oauthServer.start();
        WireMock.configureFor("localhost", OAUTH_PORT);
        apiServer = new WireMockServer(API_PORT);
        apiServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (apiServer != null) {
            apiServer.stop();
        }
        if (oauthServer != null) {
            oauthServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        client = new GitHubOAuthClient(OAUTH_BASE, API_BASE);
        oauthServer.resetAll();
        apiServer.resetAll();
    }

    @Test
    @DisplayName("exchangeCode() posts the OAuth form and returns the access token")
    void exchangeCode_success_returnsToken() {
        oauthServer.stubFor(post(urlEqualTo("/access_token"))
                .withHeader("Accept", equalTo("application/json"))
                .withRequestBody(containing("client_id=test-client-id"))
                .withRequestBody(containing("client_secret=test-client-secret"))
                .withRequestBody(containing("code=code-abc"))
                .withRequestBody(containing("redirect_uri=" + URLEncoder.encode(OAUTH_BASE + "/callback", StandardCharsets.UTF_8)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"gho_token_123\",\"scope\":\"read:user\",\"token_type\":\"bearer\"}")));

        String token = client.exchangeCode("test-client-id", "test-client-secret", "code-abc", OAUTH_BASE + "/callback");

        assertThat(token).isEqualTo("gho_token_123");
        oauthServer.verify(1, postRequestedFor(urlEqualTo("/access_token")));
    }

    @Test
    @DisplayName("exchangeCode() throws GitHubOAuthException when GitHub rejects the code")
    void exchangeCode_badCode_throws() {
        oauthServer.stubFor(post(urlEqualTo("/access_token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"bad_verification_code\",\"error_description\":\"The code passed is incorrect or expired\"}")));

        assertThatThrownBy(() -> client.exchangeCode("id", "secret", "bad-code", "http://localhost/callback"))
                .isInstanceOf(GitHubOAuthException.class)
                .hasMessageContaining("bad_verification_code");
    }

    @Test
    @DisplayName("exchangeCode() throws GitHubOAuthException on non-200 responses")
    void exchangeCode_httpError_throws() {
        oauthServer.stubFor(post(urlEqualTo("/access_token"))
                .willReturn(aResponse().withStatus(500).withBody("boom")));

        assertThatThrownBy(() -> client.exchangeCode("id", "secret", "code", "http://localhost/callback"))
                .isInstanceOf(GitHubOAuthException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    @DisplayName("fetchUser() authenticates with the access token and returns the GitHub user")
    void fetchUser_success_returnsUser() {
        apiServer.stubFor(get(urlEqualTo("/user"))
                .withHeader("Authorization", equalTo("Bearer gho_token_123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":999,\"login\":\"octocat\",\"name\":\"The Octocat\"}")));

        GitHubOAuthClient.GitHubOAuthUser user = client.fetchUser("gho_token_123");

        assertThat(user.id()).isEqualTo(999L);
        assertThat(user.login()).isEqualTo("octocat");
    }

    @Test
    @DisplayName("fetchUser() throws GitHubOAuthException on unauthorized responses")
    void fetchUser_unauthorized_throws() {
        apiServer.stubFor(get(urlEqualTo("/user"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withBody("{\"message\":\"Bad credentials\"}")));

        assertThatThrownBy(() -> client.fetchUser("gho_bad"))
                .isInstanceOf(GitHubOAuthException.class)
                .hasMessageContaining("HTTP 401");
    }
}
