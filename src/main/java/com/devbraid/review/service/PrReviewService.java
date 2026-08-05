package com.devbraid.review.service;

import com.devbraid.changethread.entity.ChangeThread;
import com.devbraid.changethread.exception.ThreadNotFoundException;
import com.devbraid.changethread.repository.ChangeThreadRepository;
import com.devbraid.github.client.GitHubApiClient;
import com.devbraid.github.dto.response.GitHubCompareResponse;
import com.devbraid.githubapp.GitHubAppTokenService;
import com.devbraid.review.dto.response.PrReviewCommentResponse;
import com.devbraid.review.dto.response.PrReviewListItemResponse;
import com.devbraid.review.dto.response.PrReviewResponse;
import com.devbraid.review.entity.FindingSeverity;
import com.devbraid.review.entity.PrReview;
import com.devbraid.review.entity.PrReviewComment;
import com.devbraid.review.entity.ReviewStatus;
import com.devbraid.review.repository.PrReviewCommentRepository;
import com.devbraid.review.repository.PrReviewRepository;
import com.devbraid.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Orchestrates a full PR review run: fetch diff → deterministic rules → AI
 * (fallback-safe) → merge → persist → publish to GitHub. Idempotent per
 * (thread, head SHA): an existing non-failed review is returned unchanged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrReviewService {

    private final PrReviewRepository reviewRepository;
    private final PrReviewCommentRepository commentRepository;
    private final ChangeThreadRepository threadRepository;
    private final GitHubApiClient gitHubApiClient;
    private final GitHubAppTokenService tokenService;
    private final DeterministicReviewRules deterministicReviewRules;
    private final AiReviewGenerator aiReviewGenerator;
    private final ReviewPublisher reviewPublisher;

    @Transactional
    public PrReview runReview(User user, UUID threadId, int prNumber, String headSha, Long installationId) {
        ChangeThread thread = threadRepository.findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));

        PrReview existing = reviewRepository.findByThreadIdAndHeadSha(threadId, headSha).orElse(null);
        if (existing != null && existing.getStatus() != ReviewStatus.FAILED) {
            return existing;
        }

        PrReview review = reviewRepository.save(PrReview.builder()
                .thread(thread)
                .prNumber(prNumber)
                .headSha(headSha)
                .build());

        try {
            runPipeline(review, thread, headSha, installationId);
            review.setStatus(ReviewStatus.COMPLETED);
            review.setCompletedAt(OffsetDateTime.now());
            reviewRepository.save(review);

            // ponytail: publish after persist — GitHub is the single external call,
            // and a posting failure propagates to the FAILED state below.
            reviewPublisher.publish(review, tokenService.getInstallationToken(installationId));
            reviewRepository.save(review);
            log.info("Review {} completed for PR #{} ({}) with {} findings",
                    review.getId(), prNumber, headSha, review.getComments().size());
        } catch (Exception e) {
            // ponytail: catch-and-persist at a genuine boundary — the failure state is
            // durable review data, not swallowed error handling. Rethrown for the
            // async handler / controller to see.
            review.setStatus(ReviewStatus.FAILED);
            review.setError(e.getMessage());
            reviewRepository.save(review);
            throw new RuntimeException("PR review failed: " + e.getMessage(), e);
        }
        return review;
    }

    private void runPipeline(PrReview review, ChangeThread thread, String headSha, Long installationId) throws Exception {
        String[] parts = thread.getRepositoryFullName().split("/");
        String token = tokenService.getInstallationToken(installationId);
        GitHubCompareResponse compare = gitHubApiClient.compare(token, parts[0], parts[1], thread.getBaseBranch(), headSha);

        List<ReviewFinding> findings = new ArrayList<>(
                deterministicReviewRules.evaluate(compare.getCommits(), compare.getFiles()));

        AiReviewResult aiResult = aiReviewGenerator.generate(compare.getFiles(), compare.getCommits());
        if (aiResult != null) {
            if (aiResult.summary() != null && !aiResult.summary().isBlank()) {
                review.setSummary(aiResult.summary());
            }
            findings.addAll(aiResult.findings());
        }

        Map<String, Long> counts = new LinkedHashMap<>();
        for (FindingSeverity severity : FindingSeverity.values()) {
            counts.put(severity.name(), 0L);
        }
        for (ReviewFinding finding : findings) {
            counts.merge(finding.severity().name(), 1L, Long::sum);
        }
        review.setSeverityCounts(counts);

        if (review.getSummary() == null || review.getSummary().isBlank()) {
            review.setSummary(String.format(
                    "Deterministic review of %d changed file(s) across %d commit(s) found %d finding(s).",
                    compare.getFiles().size(), compare.getCommits().size(), findings.size()));
        }

        for (ReviewFinding finding : findings) {
            review.addComment(PrReviewComment.builder()
                    .filePath(finding.filePath())
                    .lineNumber(finding.lineNumber())
                    .severity(finding.severity())
                    .category(finding.category())
                    .title(finding.title())
                    .body(finding.body())
                    .build());
        }
    }

    @Transactional(readOnly = true)
    public PrReviewResponse getLatestReview(User user, UUID threadId) {
        requireOwnedThread(user, threadId);
        return reviewRepository.findByThreadIdOrderByCreatedAtDesc(threadId).stream()
                .findFirst()
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<PrReviewCommentResponse> getLatestReviewComments(User user, UUID threadId) {
        requireOwnedThread(user, threadId);
        return reviewRepository.findByThreadIdOrderByCreatedAtDesc(threadId).stream()
                .findFirst()
                .map(review -> commentRepository.findByReviewIdOrderByCreatedAtAsc(review.getId()).stream()
                        .map(this::toCommentResponse)
                        .toList())
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public Page<PrReviewListItemResponse> listReviews(User user, Pageable pageable) {
        return reviewRepository.findAllByThreadUserId(user.getId(), pageable).map(this::toListItemResponse);
    }

    private void requireOwnedThread(User user, UUID threadId) {
        threadRepository.findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));
    }

    private PrReviewResponse toResponse(PrReview review) {
        return PrReviewResponse.builder()
                .id(review.getId())
                .threadId(review.getThread().getId())
                .prNumber(review.getPrNumber())
                .headSha(review.getHeadSha())
                .status(review.getStatus())
                .summary(review.getSummary())
                .severityCounts(review.getSeverityCounts())
                .published(review.isPublished())
                .githubReviewUrl(review.getGithubReviewUrl())
                .error(review.getError())
                .createdAt(review.getCreatedAt())
                .completedAt(review.getCompletedAt())
                .comments(review.getComments().stream().map(this::toCommentResponse).toList())
                .build();
    }

    private PrReviewCommentResponse toCommentResponse(PrReviewComment comment) {
        return PrReviewCommentResponse.builder()
                .id(comment.getId())
                .filePath(comment.getFilePath())
                .lineNumber(comment.getLineNumber())
                .severity(comment.getSeverity())
                .category(comment.getCategory())
                .title(comment.getTitle())
                .body(comment.getBody())
                .githubCommentId(comment.getGithubCommentId())
                .build();
    }

    private PrReviewListItemResponse toListItemResponse(PrReview review) {
        return PrReviewListItemResponse.builder()
                .id(review.getId())
                .threadId(review.getThread().getId())
                .prNumber(review.getPrNumber())
                .headSha(review.getHeadSha())
                .status(review.getStatus())
                .published(review.isPublished())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
