package com.devbraid.review.service;

import com.devbraid.changethread.entity.ChangeThread;
import com.devbraid.changethread.exception.ThreadNotFoundException;
import com.devbraid.changethread.repository.ChangeThreadRepository;
import com.devbraid.github.client.GitHubApiClient;
import com.devbraid.github.dto.internal.RawGitHubPullRequest;
import com.devbraid.githubapp.GitHubAppTokenService;
import com.devbraid.githubapp.entity.GitHubAppInstallation;
import com.devbraid.githubapp.repository.GitHubAppInstallationRepository;
import com.devbraid.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Async entry points for the PR review. Webhook deliveries carry an
 * installation id; manual triggers resolve it (and the head SHA) first.
 * Failures land in AsyncUncaughtExceptionHandler — the review is marked FAILED
 * by {@link PrReviewService} before the exception propagates.
 */
@Component
@RequiredArgsConstructor
public class PrReviewTriggerService {

    private final PrReviewService prReviewService;
    private final ChangeThreadRepository threadRepository;
    private final GitHubAppInstallationRepository installationRepository;
    private final GitHubApiClient gitHubApiClient;
    private final GitHubAppTokenService tokenService;

    @Async
    public void triggerWebhookReview(User user, UUID threadId, int prNumber, String headSha, Long installationId) {
        prReviewService.runReview(user, threadId, prNumber, headSha, installationId);
    }

    @Async
    public void triggerManualReview(User user, UUID threadId, int prNumber) {
        ChangeThread thread = threadRepository.findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));
        Long installationId = resolveInstallationId(user);
        String[] parts = thread.getRepositoryFullName().split("/");
        String token = tokenService.getInstallationToken(installationId);
        RawGitHubPullRequest pr = gitHubApiClient.getPullRequest(token, parts[0], parts[1], prNumber);
        prReviewService.runReview(user, threadId, prNumber, pr.getHead().getSha(), installationId);
    }

    private Long resolveInstallationId(User user) {
        return installationRepository.findByUserId(user.getId()).stream()
                .findFirst()
                .map(GitHubAppInstallation::getInstallationId)
                .orElseThrow(() -> new IllegalStateException("No GitHub App installation found for this user"));
    }
}
