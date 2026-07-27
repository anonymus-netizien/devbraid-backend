package com.devbraid.github.controller;

import com.devbraid.common.exception.GlobalExceptionHandler;
import com.devbraid.github.dto.request.ConnectRequest;
import com.devbraid.github.dto.response.BranchDto;
import com.devbraid.github.dto.response.GitHubStatusResponse;
import com.devbraid.github.dto.response.GitRepositoryDto;
import com.devbraid.github.entity.GitHubConnection;
import com.devbraid.github.exception.GitHubAlreadyConnectedException;
import com.devbraid.github.exception.GitHubNotConnectedException;
import com.devbraid.github.service.GitHubConnectionService;
import com.devbraid.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("GitHubConnectionController Unit Tests")
@ExtendWith(MockitoExtension.class)
class GitHubConnectionControllerTest {

    private static final String PAT = "ghp_testToken123";
    private static final String OWNER = "testowner";
    private static final String REPO = "testrepo";
    private static final String GITHUB_USERNAME = "testuser";
    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    @Mock
    private GitHubConnectionService gitHubConnectionService;
    private GitHubConnectionController controller;
    private User testUser;

    @BeforeEach
    void setUp() {
        controller = new GitHubConnectionController(gitHubConnectionService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        testUser = User.builder()
                .id(USER_ID)
                .fullName("Test User")
                .email("test@example.com")
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(testUser, null, "ROLE_DEVELOPER"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/v1/github/connect returns 201 CREATED")
    void connect_Returns201() throws Exception {
        GitHubConnection connection = GitHubConnection.builder()
                .user(testUser)
                .githubUsername(GITHUB_USERNAME)
                .encryptedPat("encrypted".getBytes())
                .iv(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12})
                .build();

        GitHubStatusResponse response = GitHubStatusResponse.from(connection, true);

        when(gitHubConnectionService.connect(eq(PAT), any(User.class))).thenReturn(response);

        String body = objectMapper.writeValueAsString(new ConnectRequest(PAT));

        mockMvc.perform(post("/api/v1/github/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("GitHub connected successfully"))
                .andExpect(jsonPath("$.data.connected").value(true))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.githubUsername").value(GITHUB_USERNAME));
    }

    @Test
    @DisplayName("POST /api/v1/github/connect returns 409 when already connected")
    void connect_AlreadyConnected_Returns409() throws Exception {
        when(gitHubConnectionService.connect(eq(PAT), any(User.class)))
                .thenThrow(new GitHubAlreadyConnectedException("GitHub account already connected. Disconnect first."));

        String body = objectMapper.writeValueAsString(new ConnectRequest(PAT));

        mockMvc.perform(post("/api/v1/github/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("GitHub account already connected. Disconnect first."));
    }

    @Test
    @DisplayName("POST /api/v1/github/connect returns 400 for invalid request (missing PAT)")
    void connect_MissingPat_Returns400() throws Exception {
        String body = objectMapper.writeValueAsString(new ConnectRequest(""));

        mockMvc.perform(post("/api/v1/github/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("DELETE /api/v1/github/disconnect returns 200 OK")
    void disconnect_Returns200() throws Exception {
        doNothing().when(gitHubConnectionService).disconnect(any(User.class));

        mockMvc.perform(delete("/api/v1/github/disconnect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("GitHub disconnected"));
    }

    @Test
    @DisplayName("DELETE /api/v1/github/disconnect returns 404 when not connected")
    void disconnect_NotConnected_Returns404() throws Exception {
        doThrow(new GitHubNotConnectedException("No GitHub connection found"))
                .when(gitHubConnectionService).disconnect(any(User.class));

        mockMvc.perform(delete("/api/v1/github/disconnect"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("No GitHub connection found"));
    }

    @Test
    @DisplayName("GET /api/v1/github/status returns 200 OK with connection status")
    void getStatus_Returns200() throws Exception {
        GitHubConnection connection = GitHubConnection.builder()
                .user(testUser)
                .githubUsername(GITHUB_USERNAME)
                .encryptedPat("encrypted".getBytes())
                .iv(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12})
                .build();

        GitHubStatusResponse response = GitHubStatusResponse.from(connection, true);

        when(gitHubConnectionService.getStatus(any(User.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/github/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.connected").value(true))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.githubUsername").value(GITHUB_USERNAME));
    }

    @Test
    @DisplayName("GET /api/v1/github/status returns disconnected when not connected")
    void getStatus_NotConnected_ReturnsDisconnected() throws Exception {
        when(gitHubConnectionService.getStatus(any(User.class)))
                .thenReturn(GitHubStatusResponse.disconnected());

        mockMvc.perform(get("/api/v1/github/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.connected").value(false))
                .andExpect(jsonPath("$.data.valid").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/github/repos returns 200 OK with repo list")
    void listRepositories_Returns200() throws Exception {
        GitRepositoryDto repo = new GitRepositoryDto(OWNER + "/" + REPO, "main", false);

        when(gitHubConnectionService.listRepositories(any(User.class)))
                .thenReturn(List.of(repo));

        mockMvc.perform(get("/api/v1/github/repos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].fullName").value(OWNER + "/" + REPO))
                .andExpect(jsonPath("$.data[0].defaultBranch").value("main"))
                .andExpect(jsonPath("$.data[0].isPrivate").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/github/repos returns 404 when not connected")
    void listRepositories_NotConnected_Returns404() throws Exception {
        when(gitHubConnectionService.listRepositories(any(User.class)))
                .thenThrow(new GitHubNotConnectedException("Connect GitHub first to access repositories."));

        mockMvc.perform(get("/api/v1/github/repos"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/github/repos/{owner}/{repo}/branches returns 200 OK")
    void listBranches_Returns200() throws Exception {
        BranchDto branch = new BranchDto("main");

        when(gitHubConnectionService.listBranches(any(User.class), eq(OWNER), eq(REPO)))
                .thenReturn(List.of(branch));

        mockMvc.perform(get("/api/v1/github/repos/{owner}/{repo}/branches", OWNER, REPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("main"));
    }

    @Test
    @DisplayName("GET /api/v1/github/repos/{owner}/{repo}/branches returns 404 when not connected")
    void listBranches_NotConnected_Returns404() throws Exception {
        when(gitHubConnectionService.listBranches(any(User.class), eq(OWNER), eq(REPO)))
                .thenThrow(new GitHubNotConnectedException("Connect GitHub first to access repositories."));

        mockMvc.perform(get("/api/v1/github/repos/{owner}/{repo}/branches", OWNER, REPO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
