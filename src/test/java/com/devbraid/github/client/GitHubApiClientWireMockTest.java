package com.devbraid.github.client;

import com.devbraid.github.dto.internal.RawGitHubBranch;
import com.devbraid.github.dto.internal.RawGitHubRepo;
import com.devbraid.github.dto.internal.RawGitHubUser;
import com.devbraid.github.dto.response.GitHubCompareResponse;
import com.devbraid.github.exception.GitHubForbiddenException;
import com.devbraid.github.exception.GitHubNotFoundException;
import com.devbraid.github.exception.GitHubRateLimitException;
import com.devbraid.github.exception.GitHubTokenInvalidException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.*;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for GitHubApiClient using WireMock to simulate the GitHub API.
 * Tests real HTTP calls, JSON parsing, and error handling.
 */
@DisplayName("GitHubApiClient WireMock Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GitHubApiClientWireMockTest {

    private static final int PORT = 8099;
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static final String TOKEN = "ghp_testToken123456";

    private static WireMockServer wireMockServer;
    private GitHubApiClient client;

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
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        client = new GitHubApiClient(BASE_URL, mapper);
        resetAllRequests();
    }

    // ── Token Validation Tests ──

    @Test
    @Order(1)
    @DisplayName("validateToken() returns user when token is valid")
    void validateToken_ValidToken_ReturnsUser() {
        stubFor(get(urlEqualTo("/user"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"login\":\"octocat\",\"id\":12345}")));

        RawGitHubUser user = client.validateToken(TOKEN);

        assertThat(user).isNotNull();
        assertThat(user.getLogin()).isEqualTo("octocat");

        verify(1, getRequestedFor(urlEqualTo("/user"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN)));
    }

    @Test
    @Order(2)
    @DisplayName("validateToken() throws GitHubTokenInvalidException for 401")
    void validateToken_InvalidToken_ThrowsException() {
        stubFor(get(urlEqualTo("/user"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Bad credentials\"}")));

        assertThatThrownBy(() -> client.validateToken(TOKEN))
                .isInstanceOf(GitHubTokenInvalidException.class)
                .hasMessageContaining("token is invalid");

        verify(1, getRequestedFor(urlEqualTo("/user")));
    }

    @Test
    @Order(3)
    @DisplayName("validateToken() throws GitHubRateLimitException for 403")
    void validateToken_RateLimited_ThrowsException() {
        stubFor(get(urlEqualTo("/user"))
                .willReturn(aResponse()
                        .withStatus(403)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"API rate limit exceeded\"}")));

        assertThatThrownBy(() -> client.validateToken(TOKEN))
                .isInstanceOf(GitHubRateLimitException.class)
                .hasMessageContaining("rate limit");
    }

    @Test
    @Order(4)
    @DisplayName("createPullRequestComment() throws GitHubForbiddenException for 403 (missing PAT scope)")
    void createPullRequestComment_Forbidden_ThrowsException() {
        stubFor(post(urlEqualTo("/repos/owner/repo/issues/42/comments"))
                .willReturn(aResponse()
                        .withStatus(403)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Resource not accessible by personal access token\"}")));

        assertThatThrownBy(() -> client.createPullRequestComment(TOKEN, "owner", "repo", 42, "body"))
                .isInstanceOf(GitHubForbiddenException.class)
                .hasMessageContaining("scope");

        verify(1, postRequestedFor(urlEqualTo("/repos/owner/repo/issues/42/comments")));
    }

    // ── Repository Listing Tests ──

    @Test
    @Order(4)
    @DisplayName("listRepositories() returns parsed repository list")
    void listRepositories_ValidToken_ReturnsRepos() {
        String reposJson = """
                [
                    {"full_name":"octocat/Hello-World","default_branch":"master","private":false},
                    {"full_name":"octocat/Spoon-Knife","default_branch":"main","private":true}
                ]
                """;

        stubFor(get(urlEqualTo("/user/repos?per_page=100&page=1&type=all"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(reposJson)));

        List<RawGitHubRepo> repos = client.listRepositories(TOKEN);

        assertThat(repos).hasSize(2);
        assertThat(repos.get(0).getFullName()).isEqualTo("octocat/Hello-World");
        assertThat(repos.get(0).getDefaultBranch()).isEqualTo("master");
        assertThat(repos.get(0).isPrivate()).isFalse();
        assertThat(repos.get(1).getFullName()).isEqualTo("octocat/Spoon-Knife");
        assertThat(repos.get(1).isPrivate()).isTrue();

        verify(1, getRequestedFor(urlEqualTo("/user/repos?per_page=100&page=1&type=all")));
    }

    @Test
    @Order(5)
    @DisplayName("listRepositories() returns empty list when no repos")
    void listRepositories_NoRepos_ReturnsEmptyList() {
        stubFor(get(urlEqualTo("/user/repos?per_page=100&page=1&type=all"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        List<RawGitHubRepo> repos = client.listRepositories(TOKEN);

        assertThat(repos).isEmpty();
    }

    @Test
    @Order(6)
    @DisplayName("listRepositories() with custom pagination")
    void listRepositories_CustomPagination_CorrectUrl() {
        stubFor(get(urlEqualTo("/user/repos?per_page=50&page=2&type=all"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        List<RawGitHubRepo> repos = client.listRepositories(TOKEN, 50, 2);

        assertThat(repos).isEmpty();
        verify(1, getRequestedFor(urlEqualTo("/user/repos?per_page=50&page=2&type=all")));
    }

    @Test
    @Order(7)
    @DisplayName("listRepositories() throws exception for 404")
    void listRepositories_NotFound_ThrowsException() {
        stubFor(get(urlEqualTo("/user/repos?per_page=100&page=1&type=all"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withBody("{\"message\":\"Not Found\"}")));

        assertThatThrownBy(() -> client.listRepositories(TOKEN))
                .isInstanceOf(GitHubNotFoundException.class)
                .hasMessageContaining("not found");
    }

    // ── Branch Listing Tests ──

    @Test
    @Order(8)
    @DisplayName("listBranches() returns parsed branch list")
    void listBranches_ValidRepo_ReturnsBranches() {
        String branchesJson = """
                [
                    {"name":"main"},
                    {"name":"develop"},
                    {"name":"feature/new-feature"}
                ]
                """;

        stubFor(get(urlEqualTo("/repos/octocat/Hello-World/branches?per_page=100&page=1"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(branchesJson)));

        List<RawGitHubBranch> branches = client.listBranches(TOKEN, "octocat", "Hello-World");

        assertThat(branches).hasSize(3);
        assertThat(branches.get(0).getName()).isEqualTo("main");
        assertThat(branches.get(1).getName()).isEqualTo("develop");
        assertThat(branches.get(2).getName()).isEqualTo("feature/new-feature");

        verify(1, getRequestedFor(urlEqualTo("/repos/octocat/Hello-World/branches?per_page=100&page=1")));
    }

    @Test
    @Order(9)
    @DisplayName("listBranches() returns empty list when no branches")
    void listBranches_NoBranches_ReturnsEmptyList() {
        stubFor(get(urlEqualTo("/repos/octocat/Hello-World/branches?per_page=100&page=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        List<RawGitHubBranch> branches = client.listBranches(TOKEN, "octocat", "Hello-World");

        assertThat(branches).isEmpty();
    }

    @Test
    @Order(10)
    @DisplayName("listBranches() with custom pagination")
    void listBranches_CustomPagination_CorrectUrl() {
        stubFor(get(urlEqualTo("/repos/octocat/Hello-World/branches?per_page=30&page=3"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        List<RawGitHubBranch> branches = client.listBranches(TOKEN, "octocat", "Hello-World", 30, 3);

        assertThat(branches).isEmpty();
        verify(1, getRequestedFor(urlEqualTo("/repos/octocat/Hello-World/branches?per_page=30&page=3")));
    }

    // ── Compare Endpoint Tests ──

    @Test
    @Order(11)
    @DisplayName("compare() returns parsed comparison response")
    void compare_ValidBranches_ReturnsComparison() {
        String compareJson = """
                {
                    "status":"diverged",
                    "ahead_by":3,
                    "behind_by":1,
                    "total_commits":4,
                    "commits":[
                        {"sha":"abc123","message":"feat: add new feature"},
                        {"sha":"def456","message":"fix: resolve bug"}
                    ],
                    "files":[
                        {"filename":"src/main.java","status":"modified","additions":10,"deletions":2},
                        {"filename":"src/test.java","status":"added","additions":50,"deletions":0}
                    ]
                }
                """;

        stubFor(get(urlEqualTo("/repos/octocat/Hello-World/compare/main...develop"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(compareJson)));

        GitHubCompareResponse comparison = client.compare(TOKEN, "octocat", "Hello-World", "main", "develop");

        assertThat(comparison).isNotNull();
        assertThat(comparison.getStatus()).isEqualTo("diverged");
        assertThat(comparison.getAheadBy()).isEqualTo(3);
        assertThat(comparison.getBehindBy()).isEqualTo(1);
        assertThat(comparison.getTotalCommits()).isEqualTo(4);
        assertThat(comparison.getCommits()).hasSize(2);
        assertThat(comparison.getCommits().get(0).getSha()).isEqualTo("abc123");
        assertThat(comparison.getCommits().get(0).getMessage()).isEqualTo("feat: add new feature");
        assertThat(comparison.getFiles()).hasSize(2);
        assertThat(comparison.getFiles().get(0).getFilename()).isEqualTo("src/main.java");

        verify(1, getRequestedFor(urlEqualTo("/repos/octocat/Hello-World/compare/main...develop")));
    }

    @Test
    @Order(12)
    @DisplayName("compare() throws GitHubNotFoundException for invalid repo")
    void compare_InvalidRepo_ThrowsNotFoundException() {
        stubFor(get(urlEqualTo("/repos/invalid/repo/compare/main...develop"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withBody("{\"message\":\"Not Found\"}")));

        assertThatThrownBy(() -> client.compare(TOKEN, "invalid", "repo", "main", "develop"))
                .isInstanceOf(GitHubNotFoundException.class)
                .hasMessageContaining("not found");
    }

    // ── Request Headers Tests ──

    @Test
    @Order(13)
    @DisplayName("All requests include correct headers")
    void allRequests_IncludesCorrectHeaders() {
        stubFor(get(urlEqualTo("/user"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"login\":\"test\"}")));

        client.validateToken(TOKEN);

        verify(1, getRequestedFor(urlEqualTo("/user"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .withHeader("Accept", equalTo("application/vnd.github+json"))
                .withHeader("X-GitHub-Api-Version", equalTo("2022-11-28"))
                .withHeader("User-Agent", equalTo("DevBraid")));
    }

    // ── JSON Parsing Edge Cases ──

    @Test
    @Order(14)
    @DisplayName("validateToken() ignores unknown JSON fields")
    void validateToken_UnknownFields_Ignored() {
        stubFor(get(urlEqualTo("/user"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"login\":\"octocat\",\"id\":12345,\"extra\":\"ignored\",\"nested\":{\"key\":\"val\"}}")));

        RawGitHubUser user = client.validateToken(TOKEN);

        assertThat(user).isNotNull();
        assertThat(user.getLogin()).isEqualTo("octocat");
    }

    @Test
    @Order(15)
    @DisplayName("listRepositories() handles repos with missing optional fields")
    void listRepositories_MissingOptionalFields_DefaultValues() {
        String minimalJson = """
                [{"full_name":"owner/repo"}]
                """;

        stubFor(get(urlEqualTo("/user/repos?per_page=100&page=1&type=all"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(minimalJson)));

        List<RawGitHubRepo> repos = client.listRepositories(TOKEN);

        assertThat(repos).hasSize(1);
        assertThat(repos.get(0).getFullName()).isEqualTo("owner/repo");
        assertThat(repos.get(0).getDefaultBranch()).isNull();
        assertThat(repos.get(0).isPrivate()).isFalse();
    }

    // ── Server Lifecycle ──

    @Test
    @Order(16)
    @DisplayName("WireMock server is running")
    void wireMockServer_IsRunning() {
        assertThat(wireMockServer.isRunning()).isTrue();
        assertThat(wireMockServer.port()).isEqualTo(PORT);
    }
}