package com.devbraid.githubapp.service;

import com.devbraid.githubapp.client.GitHubOAuthClient;
import com.devbraid.githubapp.entity.GitHubIdentity;
import com.devbraid.githubapp.exception.GitHubOAuthException;
import com.devbraid.githubapp.exception.GitHubOAuthNotConfiguredException;
import com.devbraid.githubapp.repository.GitHubIdentityRepository;
import com.devbraid.user.entity.User;
import com.devbraid.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("GitHubOAuthService Unit Tests")
@ExtendWith(MockitoExtension.class)
class GitHubOAuthServiceTest {

    private static final String FRONTEND_URL = "http://localhost:3000";
    private static final String CALLBACK_BASE = "http://localhost:8080";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private GitHubOAuthClient oauthClient;
    @Mock
    private GitHubIdentityRepository identityRepository;
    @Mock
    private UserRepository userRepository;

    private GitHubOAuthService oauthService;
    private User testUser;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        oauthService = new GitHubOAuthService(redisTemplate, oauthClient, identityRepository, userRepository, objectMapper);
        ReflectionTestUtils.setField(oauthService, "clientId", "test-client-id");
        ReflectionTestUtils.setField(oauthService, "clientSecret", "test-client-secret");
        ReflectionTestUtils.setField(oauthService, "callbackBaseUrl", CALLBACK_BASE);
        ReflectionTestUtils.setField(oauthService, "frontendUrl", FRONTEND_URL);

        testUser = User.builder()
                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
                .fullName("Test User")
                .email("test@example.com")
                .build();
    }

    @Test
    @DisplayName("startOAuth stores a state nonce in Redis with a 10-minute TTL and builds the authorize URL")
    void startOAuth_storesStateAndBuildsAuthorizeUrl() {
        String url = oauthService.startOAuth(testUser);

        assertThat(url).startsWith("https://github.com/login/oauth/authorize?client_id=test-client-id");
        assertThat(url).contains("redirect_uri=" + URLEncoder.encode(
                CALLBACK_BASE + "/api/v1/github-app/oauth/callback", StandardCharsets.UTF_8));
        assertThat(url).contains("scope=read:user");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), valueCaptor.capture(), eq(Duration.ofMinutes(10)));
        assertThat(keyCaptor.getValue()).startsWith("githubapp:oauth:");
        assertThat(valueCaptor.getValue()).contains("\"userId\"");
        assertThat(valueCaptor.getValue()).contains("\"expiresAt\"");

        String state = keyCaptor.getValue().substring("githubapp:oauth:".length());
        assertThat(url).contains("&state=" + state);
    }

    @Test
    @DisplayName("startOAuth fails fast when OAuth credentials are not configured")
    void startOAuth_notConfigured_throws() {
        ReflectionTestUtils.setField(oauthService, "clientSecret", "");

        assertThatThrownBy(() -> oauthService.startOAuth(testUser))
                .isInstanceOf(GitHubOAuthNotConfiguredException.class)
                .hasMessage("GitHub OAuth is not configured");
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("completeOAuth happy path exchanges the code, fetches the user, and upserts the identity")
    void completeOAuth_happyPath_upsertsIdentity() {
        String state = "state-123";
        ObjectNode stored = objectMapper.createObjectNode();
        stored.put("userId", testUser.getId().toString());
        stored.put("expiresAt", OffsetDateTime.now().plusMinutes(5).toString());
        when(valueOperations.get("githubapp:oauth:" + state)).thenReturn(stored.toString());
        when(oauthClient.exchangeCode("test-client-id", "test-client-secret", "code-abc",
                CALLBACK_BASE + "/api/v1/github-app/oauth/callback")).thenReturn("gho_token_1");
        when(oauthClient.fetchUser("gho_token_1")).thenReturn(new GitHubOAuthClient.GitHubOAuthUser(999L, "octocat"));
        when(identityRepository.findByGithubUserId(999L)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(testUser.getId())).thenReturn(testUser);

        String redirect = oauthService.completeOAuth("code-abc", state);

        assertThat(redirect).isEqualTo(FRONTEND_URL + "/connections?githubApp=linked");
        verify(redisTemplate).delete("githubapp:oauth:" + state);
        verify(userRepository).getReferenceById(testUser.getId());
        ArgumentCaptor<GitHubIdentity> captor = ArgumentCaptor.forClass(GitHubIdentity.class);
        verify(identityRepository).save(captor.capture());
        assertThat(captor.getValue().getGithubUserId()).isEqualTo(999L);
        assertThat(captor.getValue().getGithubLogin()).isEqualTo("octocat");
        assertThat(captor.getValue().getUser()).isEqualTo(testUser);
    }

    @Test
    @DisplayName("completeOAuth updates the login and owner of an existing identity (relink)")
    void completeOAuth_existingIdentity_updatesLoginAndUser() {
        String state = "state-456";
        ObjectNode stored = objectMapper.createObjectNode();
        stored.put("userId", testUser.getId().toString());
        stored.put("expiresAt", OffsetDateTime.now().plusMinutes(5).toString());
        when(valueOperations.get("githubapp:oauth:" + state)).thenReturn(stored.toString());
        when(oauthClient.exchangeCode(anyString(), anyString(), anyString(), anyString())).thenReturn("gho_token_1");
        when(oauthClient.fetchUser("gho_token_1")).thenReturn(new GitHubOAuthClient.GitHubOAuthUser(999L, "renamed-user"));
        GitHubIdentity existing = GitHubIdentity.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .githubUserId(999L)
                .githubLogin("old-login")
                .build();
        when(identityRepository.findByGithubUserId(999L)).thenReturn(Optional.of(existing));

        String redirect = oauthService.completeOAuth("code-xyz", state);

        assertThat(redirect).isEqualTo(FRONTEND_URL + "/connections?githubApp=linked");
        verify(identityRepository).save(existing);
        assertThat(existing.getGithubLogin()).isEqualTo("renamed-user");
    }

    @Test
    @DisplayName("completeOAuth redirects with githubApp=error when the state is missing")
    void completeOAuth_missingState_returnsErrorRedirect() {
        when(valueOperations.get("githubapp:oauth:unknown-state")).thenReturn(null);

        String redirect = oauthService.completeOAuth("code-abc", "unknown-state");

        assertThat(redirect).isEqualTo(FRONTEND_URL + "/connections?githubApp=error");
        verifyNoInteractions(oauthClient);
    }

    @Test
    @DisplayName("completeOAuth redirects with githubApp=error when the state is expired")
    void completeOAuth_expiredState_returnsErrorRedirect() {
        String state = "state-expired";
        ObjectNode stored = objectMapper.createObjectNode();
        stored.put("userId", testUser.getId().toString());
        stored.put("expiresAt", OffsetDateTime.now().minusMinutes(1).toString());
        when(valueOperations.get("githubapp:oauth:" + state)).thenReturn(stored.toString());

        String redirect = oauthService.completeOAuth("code-abc", state);

        assertThat(redirect).isEqualTo(FRONTEND_URL + "/connections?githubApp=error");
        verify(redisTemplate).delete("githubapp:oauth:" + state);
        verifyNoInteractions(oauthClient);
    }

    @Test
    @DisplayName("completeOAuth redirects with githubApp=error when the code is rejected by GitHub")
    void completeOAuth_badCode_returnsErrorRedirect() {
        String state = "state-789";
        ObjectNode stored = objectMapper.createObjectNode();
        stored.put("userId", testUser.getId().toString());
        stored.put("expiresAt", OffsetDateTime.now().plusMinutes(5).toString());
        when(valueOperations.get("githubapp:oauth:" + state)).thenReturn(stored.toString());
        when(oauthClient.exchangeCode(anyString(), anyString(), eq("bad-code"), anyString()))
                .thenThrow(new GitHubOAuthException("GitHub OAuth token exchange failed: bad_verification_code"));

        String redirect = oauthService.completeOAuth("bad-code", state);

        assertThat(redirect).isEqualTo(FRONTEND_URL + "/connections?githubApp=error");
        verify(identityRepository, never()).save(any(GitHubIdentity.class));
    }

    @Test
    @DisplayName("completeOAuth redirects with githubApp=error when the code param is missing")
    void completeOAuth_nullCode_returnsErrorRedirect() {
        String redirect = oauthService.completeOAuth(null, "state-1");

        assertThat(redirect).isEqualTo(FRONTEND_URL + "/connections?githubApp=error");
        verifyNoInteractions(oauthClient);
    }
}
