package com.devbraid.github.client;

import com.devbraid.github.dto.internal.RawGitHubBranch;
import com.devbraid.github.dto.internal.RawGitHubOrg;
import com.devbraid.github.dto.internal.RawGitHubRepo;
import com.devbraid.github.dto.internal.RawGitHubUser;
import com.devbraid.github.dto.response.CommitSummaryDto;
import com.devbraid.github.dto.response.GitHubCompareResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Component
@Slf4j
public class GitHubApiClient {

    private static final String DEFAULT_GITHUB_API_BASE = "https://api.github.com";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiBase;

    public GitHubApiClient() {
        this(DEFAULT_GITHUB_API_BASE);
    }

    /**
     * Package-private constructor for testing — allows overriding the API base URL.
     */
    GitHubApiClient(String apiBase) {
        this.apiBase = apiBase;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Validate token and return the authenticated user.
     */
    public RawGitHubUser validateToken(String token) {
        return get("/user", token, RawGitHubUser.class);
    }

    /**
     * Convenience overload — first page, 100 repos.
     */
    public List<RawGitHubRepo> listRepositories(String token) {
        return listRepositories(token, 100, 1);
    }

    public List<RawGitHubRepo> listRepositories(String token, int perPage, int page) {
        String path = "/user/repos?per_page=" + perPage + "&page=" + page + "&type=all";
        return getList(path, token, new TypeReference<>() {
        });
    }

    public List<RawGitHubRepo> listOrgRepositories(String token, String org, int perPage, int page) {
        String path = "/orgs/" + org + "/repos?per_page=" + perPage + "&page=" + page;
        return getList(path, token, new TypeReference<>() {
        });
    }

    /**
     * Convenience overload — first page, 100 branches.
     */
    public List<RawGitHubBranch> listBranches(String token, String owner, String repo) {
        return listBranches(token, owner, repo, 100, 1);
    }

    public List<RawGitHubBranch> listBranches(String token, String owner, String repo, int perPage, int page) {
        String path = "/repos/" + owner + "/" + repo + "/branches?per_page=" + perPage + "&page=" + page;
        return getList(path, token, new TypeReference<>() {
        });
    }

    public List<RawGitHubOrg> listUserOrgs(String token) {
        return getList("/user/orgs", token, new TypeReference<>() {
        });
    }

    public GitHubCompareResponse compare(String token, String owner, String repo, String base, String head) {
        String path = "/repos/" + owner + "/" + repo + "/compare/" + base + "..." + head;
        return get(path, token, GitHubCompareResponse.class);
    }

    public List<CommitSummaryDto> listCommits(String token, String owner, String repo, String branch, int perPage) {
        String path = "/repos/" + owner + "/" + repo + "/commits?sha=" + branch + "&per_page=" + perPage;
        return getList(path, token, new TypeReference<>() {
        });
    }

    /**
     * Create a comment on a pull request.
     */
    public void createPullRequestComment(String token, String owner, String repo, int prNumber, String body) {
        String path = "/repos/" + owner + "/" + repo + "/issues/" + prNumber + "/comments";
        try {
            String jsonBody = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
                put("body", body);
            }});
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + path))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("Content-Type", "application/json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "DevBraid")
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201) {
                log.warn("GitHub API returned {} for PR comment: {}", response.statusCode(), response.body());
                throw new RuntimeException("GitHub API error: " + response.statusCode());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create PR comment", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("GitHub API request interrupted", e);
        }
    }

    // ── Private HTTP helpers ──

    private <T> T get(String path, String token, Class<T> responseType) {
        HttpRequest request = buildRequest(path, token);
        return sendAndParse(request, responseType);
    }

    private <T> List<T> getList(String path, String token, TypeReference<List<T>> typeRef) {
        HttpRequest request = buildRequest(path, token);
        return sendAndParseList(request, typeRef);
    }

    private HttpRequest buildRequest(String path, String token) {
        return HttpRequest.newBuilder()
                .uri(URI.create(apiBase + path))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "DevBraid")
                .timeout(TIMEOUT)
                .GET()
                .build();
    }

    private <T> T sendAndParse(HttpRequest request, Class<T> responseType) {
        return executeRequest(request, body -> {
            try {
                return objectMapper.readValue(body, responseType);
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse GitHub API response", e);
            }
        });
    }

    private <T> List<T> sendAndParseList(HttpRequest request, TypeReference<List<T>> typeRef) {
        return executeRequest(request, body -> {
            try {
                return objectMapper.readValue(body, typeRef);
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse GitHub API response", e);
            }
        });
    }

    private <T> T executeRequest(HttpRequest request, java.util.function.Function<String, T> parser) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status == 401) {
                throw new com.devbraid.github.exception.GitHubTokenInvalidException(
                        "GitHub token is invalid or expired. Please reconnect."
                );
            }
            if (status == 403) {
                throw new com.devbraid.github.exception.GitHubRateLimitException(
                        "GitHub API rate limit exceeded. Try again later."
                );
            }
            if (status == 404) {
                throw new com.devbraid.github.exception.GitHubNotFoundException(
                        "GitHub resource not found. Check repo and branch names."
                );
            }
            if (status == 422) {
                throw new IllegalArgumentException(
                        "GitHub API rejected request. Check branch names or repository permissions."
                );
            }
            if (status != 200) {
                log.warn("GitHub API returned {}: {}", status, response.body());
                throw new RuntimeException("GitHub API error: " + status);
            }

            return parser.apply(response.body());
        } catch (IOException e) {
            throw new RuntimeException("Failed to communicate with GitHub API", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("GitHub API request interrupted", e);
        }
    }
}
