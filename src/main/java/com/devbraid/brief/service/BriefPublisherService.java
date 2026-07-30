package com.devbraid.brief.service;

import com.devbraid.brief.dto.PublishResponse;
import com.devbraid.brief.entity.ChangeBrief;
import com.devbraid.brief.repository.ChangeBriefRepository;
import com.devbraid.changethread.entity.ChangeThread;
import com.devbraid.changethread.entity.ThreadStatus;
import com.devbraid.changethread.exception.ThreadNotFoundException;
import com.devbraid.changethread.repository.ChangeThreadRepository;
import com.devbraid.github.client.GitHubApiClient;
import com.devbraid.github.service.GitHubConnectionService;
import com.devbraid.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Publishes generated briefs to GitHub as PR comments.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BriefPublisherService {

    private final ChangeBriefRepository briefRepository;
    private final ChangeThreadRepository threadRepository;
    private final GitHubConnectionService gitHubConnectionService;
    private final GitHubApiClient gitHubApiClient;

    /**
     * Publish a brief to GitHub as a PR comment.
     *
     * @param user     the authenticated user
     * @param threadId the thread with a generated brief
     * @param prNumber the GitHub PR number to comment on
     * @return publish result
     */
    @Transactional
    public PublishResponse publishToGitHub(User user, UUID threadId, int prNumber) {
        ChangeThread thread = threadRepository
                .findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));

        ChangeBrief brief = briefRepository.findByThreadId(threadId)
                .orElseThrow(() -> new IllegalArgumentException("No brief generated yet. Generate a brief first."));

        String decryptedPat = gitHubConnectionService.getDecryptedPatForUser(user);
        String[] parts = thread.getRepositoryFullName().split("/");
        String owner = parts[0];
        String repo = parts[1];

        // ponytail: exception propagates — no graceful degradation
        gitHubApiClient.createPullRequestComment(decryptedPat, owner, repo, prNumber, brief.getContent());

        brief.setPublishedAt(OffsetDateTime.now());
        brief.setPublishUrl("https://github.com/" + thread.getRepositoryFullName() + "/pull/" + prNumber);
        briefRepository.save(brief);

        thread.setStatus(ThreadStatus.PUBLISHED);
        threadRepository.save(thread);

        log.info("Published brief {} to PR #{} on {}/{}", brief.getId(), prNumber, owner, repo);

        return PublishResponse.builder()
                .success(true)
                .publishUrl(brief.getPublishUrl())
                .message("Brief published to PR #" + prNumber)
                .publishedAt(brief.getPublishedAt())
                .build();
    }

    /**
     * Get the publish status of a thread's brief.
     */
    @Transactional(readOnly = true)
    public PublishResponse getPublishStatus(User user, UUID threadId) {
        threadRepository.findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));

        return briefRepository.findByThreadId(threadId)
                .filter(brief -> brief.getPublishedAt() != null)
                .map(brief -> getPublishResponse(brief, "Brief published"))
                .orElse(PublishResponse.builder()
                        .success(false)
                        .message("Brief not yet published")
                        .build());
    }

    private PublishResponse getPublishResponse(ChangeBrief brief, String message) {
        return PublishResponse.builder()
                .success(true)
                .publishUrl(brief.getPublishUrl())
                .message(message)
                .publishedAt(brief.getPublishedAt())
                .build();
    }


}
