package com.devbraid.github.client;

import com.devbraid.github.dto.internal.RawGitHubPullRequest;
import com.devbraid.github.dto.request.ReviewCommentRequest;
import com.devbraid.github.dto.response.GitHubReviewResponse;
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
 * WireMock tests for pull request review endpoints on GitHubApiClient.
 */
@DisplayName("GitHubApiClient PR Review WireMock Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GitHubApiClientReviewWireMockTest {

    private static final int PORT = 8100;
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

    // ── Get Pull Request Tests ──

    @Test
    @Order(1)
    @DisplayName("getPullRequest() returns parsed pull request")
    void getPullRequest_ValidPr_ReturnsParsedDto() {
        String prJson = """
                {
                    "number":42,
                    "title":"Add new feature",
                    "body":"Fixes everything",
                    "state":"open",
                    "html_url":"https://github.com/octo/repo/pull/42",
                    "head":{"ref":"feature/add","sha":"abc123"},
                    "base":{"ref":"main","sha":"def456"},
                    "user":{"login":"octocat"}
                }
                """;

        stubFor(get(urlEqualTo("/repos/octo/repo/pulls/42"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(prJson)));

        RawGitHubPullRequest pr = client.getPullRequest(TOKEN, "octo", "repo", 42);

        assertThat(pr).isNotNull();
        assertThat(pr.getNumber()).isEqualTo(42);
        assertThat(pr.getTitle()).isEqualTo("Add new feature");
        assertThat(pr.getBody()).isEqualTo("Fixes everything");
        assertThat(pr.getState()).isEqualTo("open");
        assertThat(pr.getHtmlUrl()).isEqualTo("https://github.com/octo/repo/pull/42");
        assertThat(pr.getHead().getRef()).isEqualTo("feature/add");
        assertThat(pr.getHead().getSha()).isEqualTo("abc123");
        assertThat(pr.getBase().getRef()).isEqualTo("main");
        assertThat(pr.getBase().getSha()).isEqualTo("def456");
        assertThat(pr.getUser().getLogin()).isEqualTo("octocat");

        verify(1, getRequestedFor(urlEqualTo("/repos/octo/repo/pulls/42"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN)));
    }

    // ── Create Pull Request Review Tests ──

    @Test
    @Order(2)
    @DisplayName("createPullRequestReview() posts correct JSON and returns parsed review")
    void createPullRequestReview_PostsCorrectJson_ReturnsReview() {
        stubFor(post(urlEqualTo("/repos/octo/repo/pulls/42/reviews"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":123,\"html_url\":\"https://github.com/octo/repo/pull/42#pullrequestreview-123\"}")));

        List<ReviewCommentRequest> comments = List.of(
                new ReviewCommentRequest("src/main/java/App.java", 10, "RIGHT", "This could be simplified")
        );

        GitHubReviewResponse review = client.createPullRequestReview(
                TOKEN, "octo", "repo", 42, "LGTM with one comment", "COMMENT", comments);

        assertThat(review).isNotNull();
        assertThat(review.getId()).isEqualTo(123L);
        assertThat(review.getHtmlUrl()).isEqualTo("https://github.com/octo/repo/pull/42#pullrequestreview-123");

        String requestBody = lastRequestBody();
        assertThat(requestBody).contains("LGTM with one comment");
        assertThat(requestBody).contains("\"event\":\"COMMENT\"");
        assertThat(requestBody).contains("\"path\":\"src/main/java/App.java\"");
        assertThat(requestBody).contains("\"line\":10");
        assertThat(requestBody).contains("\"side\":\"RIGHT\"");
        assertThat(requestBody).contains("\"body\":\"This could be simplified\"");

        verify(1, postRequestedFor(urlEqualTo("/repos/octo/repo/pulls/42/reviews"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .withHeader("Accept", equalTo("application/vnd.github+json"))
                .withHeader("Content-Type", equalTo("application/json")));
    }

    @Test
    @Order(3)
    @DisplayName("createPullRequestReview() throws GitHubTokenInvalidException for 401")
    void createPullRequestReview_Unauthorized_ThrowsTokenInvalidException() {
        stubFor(post(urlEqualTo("/repos/octo/repo/pulls/42/reviews"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Bad credentials\"}")));

        assertThatThrownBy(() -> client.createPullRequestReview(
                TOKEN, "octo", "repo", 42, "Looks good", "COMMENT", List.of()))
                .isInstanceOf(GitHubTokenInvalidException.class)
                .hasMessageContaining("token is invalid");

        verify(1, postRequestedFor(urlEqualTo("/repos/octo/repo/pulls/42/reviews")));
    }

    @Test
    @Order(4)
    @DisplayName("createPullRequestReview() with empty comments omits comments and event keys")
    void createPullRequestReview_EmptyComments_OmitsCommentsKey() {
        stubFor(post(urlEqualTo("/repos/octo/repo/pulls/42/reviews"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":456,\"html_url\":\"https://github.com/octo/repo/pull/42#pullrequestreview-456\"}")));

        client.createPullRequestReview(TOKEN, "octo", "repo", 42, "Just a summary", null, List.of());

        String requestBody = lastRequestBody();
        assertThat(requestBody).contains("Just a summary");
        assertThat(requestBody).doesNotContain("comments");
        assertThat(requestBody).doesNotContain("event");
    }

    // ── Server Lifecycle ──

    @Test
    @Order(5)
    @DisplayName("WireMock server is running")
    void wireMockServer_IsRunning() {
        assertThat(wireMockServer.isRunning()).isTrue();
        assertThat(wireMockServer.port()).isEqualTo(PORT);
    }

    private String lastRequestBody() {
        return wireMockServer.getAllServeEvents().getLast().getRequest().getBodyAsString();
    }
}
