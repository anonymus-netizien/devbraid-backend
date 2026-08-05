package com.devbraid.githubapp.service;

import com.devbraid.githubapp.client.GitHubOAuthClient;
import com.devbraid.githubapp.entity.GitHubIdentity;
import com.devbraid.githubapp.exception.GitHubOAuthException;
import com.devbraid.githubapp.exception.GitHubOAuthNotConfiguredException;
import com.devbraid.githubapp.repository.GitHubIdentityRepository;
import com.devbraid.user.entity.User;
import com.devbraid.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * GitHub OAuth identity linking: issues one-time state nonces (Redis, 10-min TTL),
 * exchanges the callback code for an access token, fetches the GitHub user and
 * upserts a {@link GitHubIdentity} row.
 * <p>
 * Both endpoints return URLs rather than envelope bodies where a browser is
 * involved: {@code /start} returns the authorize URL in the ApiResponse envelope
 * (the frontend opens it), {@code /callback} 302-redirects to the frontend with
 * {@code ?githubApp=linked} on success or {@code ?githubApp=error} on failure.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubOAuthService {

    private static final String OAUTH_STATE_PREFIX = "githubapp:oauth:";
    private static final Duration OAUTH_STATE_TTL = Duration.ofMinutes(10);
    private static final String AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String CALLBACK_PATH = "/api/v1/github-app/oauth/callback";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final GitHubOAuthClient oauthClient;
    private final GitHubIdentityRepository identityRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Value("${github.oauth.client-id:}")
    private String clientId;

    @Value("${github.oauth.client-secret:}")
    private String clientSecret;

    @Value("${github.oauth.callback-base-url:http://localhost:8080}")
    private String callbackBaseUrl;

    @Value("${github.oauth.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * Generate a one-time state nonce, store it in Redis, and return the GitHub
     * authorize URL the user's browser should be sent to.
     */
    public String startOAuth(User user) {
        requireConfigured();
        String state = generateState();
        storeState(state, user.getId());
        return AUTHORIZE_URL
                + "?client_id=" + urlEncode(clientId)
                + "&redirect_uri=" + urlEncode(redirectUri())
                + "&state=" + state
                + "&scope=read:user";
    }

    /**
     * Validate + consume the state, exchange the code, fetch the GitHub user,
     * upsert the identity, and return the frontend redirect URL.
     * Any failure (missing/expired state, rejected code, failed user fetch)
     * redirects to the frontend with {@code githubApp=error}.
     */
    @Transactional
    public String completeOAuth(String code, String state) {
        // Consume the state first — it is single-use, and GitHub echoes it back even
        // when the user denies the authorize screen (no code).
        OAuthState stored = consumeState(state);
        if (stored == null || stored.expiresAt().isBefore(OffsetDateTime.now())) {
            log.warn("GitHub OAuth callback with missing or expired state");
            return errorRedirect();
        }
        if (code == null || code.isBlank()) {
            return errorRedirect();
        }
        try {
            // Catch-and-translate at the OAuth boundary: the browser is mid-flow and
            // can only be redirected, so a failed exchange becomes an error redirect.
            String accessToken = oauthClient.exchangeCode(clientId, clientSecret, code, redirectUri());
            GitHubOAuthClient.GitHubOAuthUser ghUser = oauthClient.fetchUser(accessToken);
            upsertIdentity(stored.userId(), ghUser);
            log.info("Linked GitHub identity {} ({}) to user {}", ghUser.id(), ghUser.login(), stored.userId());
            return frontendUrl + "/connections?githubApp=linked";
        } catch (GitHubOAuthException e) {
            log.warn("GitHub OAuth callback failed: {}", e.getMessage());
            return errorRedirect();
        }
    }

    private void upsertIdentity(UUID userId, GitHubOAuthClient.GitHubOAuthUser ghUser) {
        User user = userRepository.getReferenceById(userId);
        GitHubIdentity identity = identityRepository.findByGithubUserId(ghUser.id())
                .orElseGet(() -> GitHubIdentity.builder()
                        .user(user)
                        .githubUserId(ghUser.id())
                        .build());
        identity.setUser(user);
        identity.setGithubLogin(ghUser.login());
        identityRepository.save(identity);
    }

    private void requireConfigured() {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new GitHubOAuthNotConfiguredException("GitHub OAuth is not configured");
        }
    }

    private String redirectUri() {
        return callbackBaseUrl + CALLBACK_PATH;
    }

    private String errorRedirect() {
        return frontendUrl + "/connections?githubApp=error";
    }

    private String generateState() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private void storeState(String state, UUID userId) {
        try {
            String json = objectMapper.writeValueAsString(
                    new OAuthState(userId, OffsetDateTime.now().plus(OAUTH_STATE_TTL)));
            redisTemplate.opsForValue().set(OAUTH_STATE_PREFIX + state, json, OAUTH_STATE_TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize OAuth state", e);
        }
    }

    private OAuthState consumeState(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        String key = OAUTH_STATE_PREFIX + state;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        redisTemplate.delete(key);
        try {
            return objectMapper.readValue(json, OAuthState.class);
        } catch (JsonProcessingException e) {
            log.warn("GitHub OAuth state payload could not be parsed — treating as consumed");
            return null;
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record OAuthState(UUID userId, OffsetDateTime expiresAt) {
    }
}
