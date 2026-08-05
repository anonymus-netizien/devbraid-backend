package com.devbraid.githubapp;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

/**
 * WireMock + Mockito tests for GitHubAppTokenService token exchange and caching.
 */
@DisplayName("GitHubAppTokenService WireMock Integration Tests")
class GitHubAppTokenServiceTest {

    private static final int PORT = 8098;
    private static final String BASE_URL = "http://localhost:" + PORT;

    private static WireMockServer wireMockServer;

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private GitHubAppJwtProvider jwtProvider;
    private GitHubAppTokenService tokenService;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(PORT);
        wireMockServer.start();
        WireMock.configureFor("localhost", PORT);
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        valueOperations = mock(ValueOperations.class);
        redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        jwtProvider = mock(GitHubAppJwtProvider.class);
        when(jwtProvider.createAppJwt()).thenReturn("test-jwt");
        tokenService = new GitHubAppTokenService(redisTemplate, jwtProvider);
        tokenService.setApiBase(BASE_URL);
        wireMockServer.resetAll();
    }

    @Test
    @DisplayName("getInstallationToken() exchanges an app JWT for an installation token on cache miss")
    void getInstallationToken_CacheMiss_ExchangesAndCaches() {
        stubFor(post(urlEqualTo("/app/installations/1/access_tokens"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"token\":\"ghs_token123\",\"expires_at\":\"2030-01-01T00:00:00Z\"}")));

        String token = tokenService.getInstallationToken(1L);

        assertThat(token).isEqualTo("ghs_token123");
        verify(1, postRequestedFor(urlEqualTo("/app/installations/1/access_tokens"))
                .withHeader("Authorization", matching("Bearer .*"))
                .withHeader("Accept", equalTo("application/vnd.github+json"))
                .withHeader("X-GitHub-Api-Version", equalTo("2022-11-28"))
                .withHeader("User-Agent", equalTo("DevBraid")));
        verify(jwtProvider).createAppJwt();
        verify(valueOperations).set(eq("githubapp:token:1"), eq("ghs_token123"), any(Duration.class));
    }

    @Test
    @DisplayName("getInstallationToken() returns the cached token without a new HTTP call")
    void getInstallationToken_CacheHit_ReturnsCachedToken() {
        when(valueOperations.get("githubapp:token:1")).thenReturn(null, "ghs_token123");
        stubFor(post(urlEqualTo("/app/installations/1/access_tokens"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"token\":\"ghs_token123\",\"expires_at\":\"2030-01-01T00:00:00Z\"}")));

        String first = tokenService.getInstallationToken(1L);
        String second = tokenService.getInstallationToken(1L);

        assertThat(first).isEqualTo("ghs_token123");
        assertThat(second).isEqualTo("ghs_token123");
        verify(1, postRequestedFor(urlEqualTo("/app/installations/1/access_tokens")));
        verify(jwtProvider, times(1)).createAppJwt();
        verify(valueOperations, times(1)).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("getInstallationToken() throws GitHubAppTokenException for non-201 responses")
    void getInstallationToken_ErrorStatus_ThrowsGitHubAppTokenException() {
        stubFor(post(urlEqualTo("/app/installations/1/access_tokens"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Bad credentials\"}")));

        assertThatThrownBy(() -> tokenService.getInstallationToken(1L))
                .isInstanceOf(GitHubAppTokenException.class)
                .hasMessage("GitHub App token exchange failed: HTTP 401");
    }
}
