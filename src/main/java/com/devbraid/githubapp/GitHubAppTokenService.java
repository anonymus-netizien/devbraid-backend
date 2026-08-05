package com.devbraid.githubapp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubAppTokenService {

    private static final String DEFAULT_GITHUB_API_BASE = "https://api.github.com";
    private static final String TOKEN_CACHE_PREFIX = "githubapp:token:";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration TTL_SAFETY_MARGIN = Duration.ofMinutes(5);
    private static final Duration MAX_CACHE_TTL = Duration.ofMinutes(55);

    private final StringRedisTemplate redisTemplate;
    private final GitHubAppJwtProvider jwtProvider;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private String apiBase = DEFAULT_GITHUB_API_BASE;

    /**
     * Return a cached installation access token, exchanging an app JWT when stale.
     */
    public String getInstallationToken(Long installationId) {
        String cacheKey = TOKEN_CACHE_PREFIX + installationId;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }
        TokenResponse response = exchangeToken(installationId, jwtProvider.createAppJwt());
        cacheToken(cacheKey, response.token(), response.expires_at());
        return response.token();
    }

    /**
     * Package-private for tests — allows overriding the GitHub API base URL.
     */
    void setApiBase(String apiBase) {
        this.apiBase = apiBase;
    }

    private TokenResponse exchangeToken(Long installationId, String appJwt) {
        try {
            String path = "/app/installations/" + installationId + "/access_tokens";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + path))
                    .header("Authorization", "Bearer " + appJwt)
                    .header("Accept", "application/vnd.github+json")
                    .header("Content-Type", "application/json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "DevBraid")
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201) {
                log.warn("GitHub App token exchange returned {}: {}", response.statusCode(), response.body());
                throw new GitHubAppTokenException("GitHub App token exchange failed: HTTP " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), TokenResponse.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to exchange GitHub App token", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("GitHub App token exchange interrupted", e);
        }
    }

    private void cacheToken(String cacheKey, String token, String expiresAt) {
        long ttlSeconds = OffsetDateTime.parse(expiresAt).toEpochSecond()
                - Instant.now().getEpochSecond()
                - TTL_SAFETY_MARGIN.toSeconds();
        if (ttlSeconds > 0) {
            redisTemplate.opsForValue().set(cacheKey, token,
                    Duration.ofSeconds(Math.min(ttlSeconds, MAX_CACHE_TTL.toSeconds())));
        }
    }

    private record TokenResponse(String token, String expires_at) {
    }
}
