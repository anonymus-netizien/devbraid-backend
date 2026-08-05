package com.devbraid.review.service;

import com.devbraid.github.client.GitHubApiClient;
import com.devbraid.github.dto.request.ReviewCommentRequest;
import com.devbraid.github.dto.response.GitHubReviewResponse;
import com.devbraid.review.entity.FindingSeverity;
import com.devbraid.review.entity.PrReview;
import com.devbraid.review.entity.PrReviewComment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Posts a completed review back to GitHub: line-level findings as inline review
 * comments, everything else in the summary body. No try/catch — a posting
 * failure propagates and the review is marked FAILED by the caller.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewPublisher {

    private final GitHubApiClient gitHubApiClient;

    private static int severityOrder(String name) {
        try {
            return FindingSeverity.valueOf(name).ordinal();
        } catch (IllegalArgumentException e) {
            return FindingSeverity.values().length;
        }
    }

    public void publish(PrReview review, String installationToken) {
        String[] parts = review.getThread().getRepositoryFullName().split("/");

        List<ReviewCommentRequest> comments = review.getComments().stream()
                .filter(c -> c.getLineNumber() != null)
                .map(c -> new ReviewCommentRequest(c.getFilePath(), c.getLineNumber(), "RIGHT",
                        "**" + c.getSeverity() + " · " + c.getTitle() + "**\n\n" + c.getBody()))
                .toList();

        GitHubReviewResponse response = gitHubApiClient.createPullRequestReview(
                installationToken, parts[0], parts[1], review.getPrNumber(),
                buildSummary(review), "COMMENT", comments);

        review.setGithubReviewId(response.getId());
        review.setGithubReviewUrl(response.getHtmlUrl());
        review.setPublished(true);

        log.info("Published review {} to PR #{} on {}/{} ({} inline comments)",
                review.getId(), review.getPrNumber(), parts[0], parts[1], comments.size());
    }

    private String buildSummary(PrReview review) {
        List<PrReviewComment> comments = review.getComments();
        StringBuilder sb = new StringBuilder("## DevBraid Review\n\n")
                .append(comments.size()).append(" finding(s)");
        if (review.getSeverityCounts() != null && !review.getSeverityCounts().isEmpty()) {
            sb.append(" · ").append(review.getSeverityCounts().entrySet().stream()
                    .sorted(Comparator.comparingInt(e -> severityOrder(e.getKey())))
                    .map(e -> e.getKey().toLowerCase() + " " + e.getValue())
                    .collect(Collectors.joining(" · ")));
        }
        sb.append("\n\n");
        if (review.getSummary() != null && !review.getSummary().isBlank()) {
            sb.append(review.getSummary()).append("\n\n");
        }

        List<PrReviewComment> lineComments = comments.stream()
                .filter(c -> c.getLineNumber() != null).toList();
        if (!lineComments.isEmpty()) {
            sb.append("| Severity | File | Line | Finding |\n|---|---|---|---|\n");
            for (PrReviewComment c : lineComments) {
                sb.append("| ").append(c.getSeverity()).append(" | ").append(c.getFilePath())
                        .append(" | ").append(c.getLineNumber()).append(" | ").append(c.getTitle()).append(" |\n");
            }
            sb.append('\n');
        }

        List<PrReviewComment> fileLevel = comments.stream()
                .filter(c -> c.getLineNumber() == null).toList();
        if (!fileLevel.isEmpty()) {
            sb.append("### File-level findings\n");
            for (PrReviewComment c : fileLevel) {
                sb.append("- **[").append(c.getSeverity()).append("] ").append(c.getTitle()).append("**")
                        .append(c.getFilePath() == null ? "" : " (" + c.getFilePath() + ")")
                        .append(": ").append(c.getBody()).append('\n');
            }
        }
        return sb.toString();
    }
}
