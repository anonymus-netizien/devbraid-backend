package com.devbraid.githubapp.client;

import com.devbraid.githubapp.exception.GitHubOAuthException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Thin HTTP client for GitHub's OAuth endpoints (token exchange on github.com,
 * user fetch on api.github.com). Uses the JDK HttpClient — same style as
 * GitHubApiClient / GitHubAppTokenService.
 * <p>
 * Checked IOException/InterruptedException are translated to GitHubOAuthException
 * at this boundary — the only try-catches in the class.
 */
@Slf4j
@Component
public class GitHubOAuthClient {

    private static final String DEFAULT_OAUTH_BASE = "https://github.com/login/oauth";
    private static final String DEFAULT_API_BASE = "https://api.github.com";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final String oauthBase;
    private final String apiBase;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GitHubOAuthClient() {
        this(DEFAULT_OAUTH_BASE, DEFAULT_API_BASE);
    }

    /**
     * Package-private constructor for tests — allows pointing the two external
     * hosts at separate WireMock servers.
     */
    GitHubOAuthClient(String oauthBase, String apiBase) {
        this.oauthBase = oauthBase;
        this.apiBase = apiBase;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Exchange an OAuth code for an access token.
     *
     * @throws GitHubOAuthException if GitHub rejects the code or the call fails
     */
    public String exchangeCode(String clientId, String clientSecret, String code, String redirectUri) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        form.put("code", code);
        form.put("redirect_uri", redirectUri);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(oauthBase + "/access_token"))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "DevBraid")
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(encodeForm(form)))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("GitHub OAuth token exchange returned {}: {}", response.statusCode(), response.body());
                throw new GitHubOAuthException("GitHub OAuth token exchange failed: HTTP " + response.statusCode());
            }
            TokenResponse tokenResponse = objectMapper.readValue(response.body(), TokenResponse.class);
            if (tokenResponse.access_token() == null) {
                String error = tokenResponse.error() != null ? tokenResponse.error() : "missing access_token";
                log.warn("GitHub OAuth token exchange failed: {}", error);
                throw new GitHubOAuthException("GitHub OAuth token exchange failed: " + error);
            }
            return tokenResponse.access_token();
        } catch (IOException e) {
            throw new GitHubOAuthException("Failed to exchange GitHub OAuth code", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubOAuthException("GitHub OAuth exchange interrupted", e);
        }
    }

    /**
     * Fetch the authenticated GitHub user for an OAuth access token.
     *
     * @throws GitHubOAuthException if the user fetch fails
     */
    public GitHubOAuthUser fetchUser(String accessToken) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBase + "/user"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "DevBraid")
                .timeout(TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("GitHub API returned {} for /user", response.statusCode());
                throw new GitHubOAuthException("GitHub user fetch failed: HTTP " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), GitHubOAuthUser.class);
        } catch (IOException e) {
            throw new GitHubOAuthException("Failed to fetch GitHub user", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubOAuthException("GitHub user fetch interrupted", e);
        }
    }

    private static String encodeForm(Map<String, String> form) {
        return form.entrySet().stream()
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record TokenResponse(String access_token, String error, String error_description) {
    }

    public record GitHubOAuthUser(Long id, String login) {
    }
}
