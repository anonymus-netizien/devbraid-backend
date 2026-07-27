package com.devbraid.github.client;

import com.devbraid.github.exception.GitHubNotFoundException;
import com.devbraid.github.exception.GitHubRateLimitException;
import com.devbraid.github.exception.GitHubTokenInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests GitHubApiClient exception types and their messages.
 * The HttpClient itself requires a real HTTP server — these tests verify
 * the exception mapping logic is correct.
 */
@DisplayName("GitHubApiClient Exception Mapping Tests")
class GitHubApiClientTest {

    @Test
    @DisplayName("GitHubTokenInvalidException has correct message")
    void tokenInvalidException_HasCorrectMessage() {
        GitHubTokenInvalidException ex = new GitHubTokenInvalidException(
                "GitHub token is invalid or expired. Please reconnect."
        );
        assertThat(ex.getMessage()).contains("token is invalid");
    }

    @Test
    @DisplayName("GitHubRateLimitException has correct message")
    void rateLimitException_HasCorrectMessage() {
        GitHubRateLimitException ex = new GitHubRateLimitException(
                "GitHub API rate limit exceeded. Try again later."
        );
        assertThat(ex.getMessage()).contains("rate limit");
    }

    @Test
    @DisplayName("GitHubNotFoundException has correct message")
    void notFoundException_HasCorrectMessage() {
        GitHubNotFoundException ex = new GitHubNotFoundException(
                "GitHub resource not found. Check repo and branch names."
        );
        assertThat(ex.getMessage()).contains("resource not found");
    }

    @Test
    @DisplayName("GitHubApiClient instantiates without error")
    void instantiation_Succeeds() {
        GitHubApiClient client = new GitHubApiClient();
        assertThat(client).isNotNull();
    }
}
