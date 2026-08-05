package com.devbraid.githubapp.controller;

import com.devbraid.githubapp.service.GitHubOAuthService;
import com.devbraid.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolverComposite;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("GitHubOAuthController Unit Tests")
@ExtendWith(MockitoExtension.class)
class GitHubOAuthControllerTest {

    private MockMvc mockMvc;
    @Mock
    private GitHubOAuthService oauthService;
    private GitHubOAuthController controller;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
                .fullName("Test User")
                .email("test@example.com")
                .build();

        controller = new GitHubOAuthController(oauthService);
        HandlerMethodArgumentResolver composite = new HandlerMethodArgumentResolverComposite()
                .addResolver(new AuthenticationPrincipalArgumentResolver());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(composite)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(testUser, null, "ROLE_DEVELOPER"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/github-app/oauth/start returns the GitHub authorize URL in the envelope")
    void startOAuth_returnsAuthorizeUrl() throws Exception {
        String authorizeUrl = "https://github.com/login/oauth/authorize?client_id=x&state=abc&scope=read:user";
        when(oauthService.startOAuth(testUser)).thenReturn(authorizeUrl);

        mockMvc.perform(get("/api/v1/github-app/oauth/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(authorizeUrl));

        verify(oauthService).startOAuth(testUser);
    }

    @Test
    @DisplayName("GET /api/v1/github-app/oauth/callback redirects to the frontend on success")
    void callback_success_redirectsToLinked() throws Exception {
        when(oauthService.completeOAuth("code-abc", "state-1"))
                .thenReturn("http://localhost:3000/connections?githubApp=linked");

        mockMvc.perform(get("/api/v1/github-app/oauth/callback")
                        .param("code", "code-abc")
                        .param("state", "state-1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/connections?githubApp=linked"));
    }

    @Test
    @DisplayName("GET /api/v1/github-app/oauth/callback redirects to the frontend error page on failure")
    void callback_failure_redirectsToError() throws Exception {
        when(oauthService.completeOAuth("bad-code", "stale-state"))
                .thenReturn("http://localhost:3000/connections?githubApp=error");

        mockMvc.perform(get("/api/v1/github-app/oauth/callback")
                        .param("code", "bad-code")
                        .param("state", "stale-state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/connections?githubApp=error"));
    }

    @Test
    @DisplayName("GET /api/v1/github-app/oauth/callback without params still redirects (service decides)")
    void callback_noParams_stillRedirects() throws Exception {
        when(oauthService.completeOAuth(null, null))
                .thenReturn("http://localhost:3000/connections?githubApp=error");

        mockMvc.perform(get("/api/v1/github-app/oauth/callback"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/connections?githubApp=error"));
    }
}
