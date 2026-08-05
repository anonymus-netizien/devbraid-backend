package com.devbraid.review.service;

import com.devbraid.changethread.entity.ChangeThread;
import com.devbraid.github.client.GitHubApiClient;
import com.devbraid.github.dto.request.ReviewCommentRequest;
import com.devbraid.github.dto.response.GitHubReviewResponse;
import com.devbraid.review.entity.FindingCategory;
import com.devbraid.review.entity.FindingSeverity;
import com.devbraid.review.entity.PrReview;
import com.devbraid.review.entity.PrReviewComment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ReviewPublisher Unit Tests")
@ExtendWith(MockitoExtension.class)
class ReviewPublisherTest {

    @InjectMocks
    private ReviewPublisher reviewPublisher;
    @Mock
    private GitHubApiClient gitHubApiClient;

    @Test
    @DisplayName("publish() posts inline comments for line-level findings and updates the review")
    void publish_postsInlineCommentsAndUpdatesReview() {
        ChangeThread thread = org.mockito.Mockito.mock(ChangeThread.class);
        when(thread.getRepositoryFullName()).thenReturn("octo/repo");
        when(gitHubApiClient.createPullRequestReview(anyString(), eq("octo"), eq("repo"), eq(42),
                anyString(), eq("COMMENT"), anyList()))
                .thenReturn(new GitHubReviewResponse(7L, "https://github.com/octo/repo/pull/42#pullrequestreview-7"));

        PrReview review = PrReview.builder()
                .thread(thread)
                .prNumber(42)
                .headSha("abc123")
                .summary("Two findings")
                .severityCounts(Map.of("HIGH", 1L, "INFO", 1L))
                .build();
        review.addComment(PrReviewComment.builder()
                .filePath("src/main/java/App.java").lineNumber(3)
                .severity(FindingSeverity.HIGH).category(FindingCategory.BUG)
                .title("Null deref").body("can be null")
                .build());
        review.addComment(PrReviewComment.builder()
                .filePath(null).lineNumber(null)
                .severity(FindingSeverity.INFO).category(FindingCategory.OTHER)
                .title("Large diff").body("review carefully")
                .build());

        reviewPublisher.publish(review, "installation-token");

        verify(gitHubApiClient).createPullRequestReview(eq("installation-token"), eq("octo"), eq("repo"),
                eq(42), org.mockito.ArgumentMatchers.contains("Null deref"), eq("COMMENT"),
                org.mockito.ArgumentMatchers.argThat(comments -> {
                    assertThat(comments).hasSize(1);
                    ReviewCommentRequest comment = comments.get(0);
                    assertThat(comment.getPath()).isEqualTo("src/main/java/App.java");
                    assertThat(comment.getLine()).isEqualTo(3);
                    assertThat(comment.getSide()).isEqualTo("RIGHT");
                    assertThat(comment.getBody()).contains("Null deref");
                    return true;
                }));
        assertThat(review.getGithubReviewId()).isEqualTo(7L);
        assertThat(review.getGithubReviewUrl()).contains("pullrequestreview-7");
        assertThat(review.isPublished()).isTrue();
    }

    @Test
    @DisplayName("publish() still posts a summary review when there are no inline comments")
    void publish_noInlineComments_postsSummaryOnly() {
        ChangeThread thread = org.mockito.Mockito.mock(ChangeThread.class);
        when(thread.getRepositoryFullName()).thenReturn("octo/repo");
        when(gitHubApiClient.createPullRequestReview(anyString(), anyString(), anyString(), anyInt(),
                anyString(), anyString(), anyList()))
                .thenReturn(new GitHubReviewResponse(8L, "https://example.com"));

        PrReview review = PrReview.builder()
                .thread(thread)
                .prNumber(42)
                .headSha("abc123")
                .severityCounts(Map.of())
                .build();

        reviewPublisher.publish(review, "token");

        verify(gitHubApiClient).createPullRequestReview(anyString(), anyString(), anyString(), anyInt(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.argThat(List::isEmpty));
        assertThat(review.isPublished()).isTrue();
    }
}
