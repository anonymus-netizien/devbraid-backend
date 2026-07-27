package com.devbraid.github.dto.response;

import com.devbraid.github.entity.GitHubConnection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubStatusResponse {
    private boolean connected;
    private boolean valid;
    private String githubUsername;
    private OffsetDateTime connectedAt;
    private OffsetDateTime lastValidatedAt;

    public static GitHubStatusResponse from(GitHubConnection entity, boolean valid) {
        return GitHubStatusResponse.builder()
                .connected(true)
                .valid(valid)
                .githubUsername(entity.getGithubUsername())
                .connectedAt(entity.getConnectedAt())
                .lastValidatedAt(entity.getLastValidated())
                .build();
    }

    public static GitHubStatusResponse disconnected() {
        return GitHubStatusResponse.builder()
                .connected(false)
                .valid(false)
                .build();
    }

    public static GitHubStatusResponse invalid(GitHubConnection entity) {
        return GitHubStatusResponse.builder()
                .connected(true)
                .valid(false)
                .githubUsername(entity.getGithubUsername())
                .connectedAt(entity.getConnectedAt())
                .build();
    }
}
