package com.devbraid.review.service;

import com.devbraid.changethread.entity.ChangeThread;
import com.devbraid.changethread.exception.ThreadNotFoundException;
import com.devbraid.changethread.repository.ChangeThreadRepository;
import com.devbraid.github.client.GitHubApiClient;
import com.devbraid.github.dto.response.ChangedFileDto;
import com.devbraid.github.dto.response.GitHubCompareResponse;
import com.devbraid.githubapp.GitHubAppTokenService;
import com.devbraid.review.entity.FindingCategory;
import com.devbraid.review.entity.FindingSeverity;
import com.devbraid.review.entity.PrReview;
import com.devbraid.review.entity.ReviewStatus;
import com.devbraid.review.repository.PrReviewCommentRepository;
import com.devbraid.review.repository.PrReviewRepository;
import com.devbraid.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("PrReviewService Unit Tests")
@ExtendWith(MockitoExtension.class)
class PrReviewServiceTest {

    private final UUID userId = UUID.randomUUID();
    @Mock
    private PrReviewRepository reviewRepository;
    @Mock
    private PrReviewCommentRepository commentRepository;
    @Mock
    private ChangeThreadRepository threadRepository;
    @Mock
    private GitHubApiClient gitHubApiClient;
    @Mock
    private GitHubAppTokenService tokenService;
    @Mock
    private DeterministicReviewRules deterministicReviewRules;
    @Mock
    private AiReviewGenerator aiReviewGenerator;
    @Mock
    private ReviewPublisher reviewPublisher;
    @InjectMocks
    private PrReviewService prReviewService;
    private User user;
    private ChangeThread thread;
    private UUID threadId;

    @BeforeEach
    void setUp() {
        user = org.mockito.Mockito.mock(User.class);
        thread = org.mockito.Mockito.mock(ChangeThread.class);
        threadId = UUID.randomUUID();
        lenient().when(thread.getRepositoryFullName()).thenReturn("octo/repo");
        lenient().when(thread.getBaseBranch()).thenReturn("main");
        lenient().when(user.getId()).thenReturn(userId);
    }

    @Test
    @DisplayName("runReview() completes deterministic-only review when AI is unavailable")
    void runReview_aiUnavailable_completesDeterministicReview() throws Exception {
        when(threadRepository.findByIdAndUserId(threadId, userId)).thenReturn(Optional.of(thread));
        when(reviewRepository.findByThreadIdAndHeadSha(threadId, "abc123")).thenReturn(Optional.empty());
        when(reviewRepository.save(any(PrReview.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenService.getInstallationToken(1L)).thenReturn("token");
        when(gitHubApiClient.compare("token", "octo", "repo", "main", "abc123"))
                .thenReturn(new GitHubCompareResponse("ahead", 1, 0, 1,
                        List.of(), List.of(new ChangedFileDto("App.java", "modified", 1, 1, "@@ -1 +1 @@\n+x"))));
        when(deterministicReviewRules.evaluate(any(), any())).thenReturn(List.of(
                new ReviewFinding(FindingSeverity.MEDIUM, FindingCategory.OTHER, null, null, "largeDiff", "Big change")));
        when(aiReviewGenerator.generate(any(), any())).thenReturn(null);

        PrReview review = prReviewService.runReview(user, threadId, 42, "abc123", 1L);

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.COMPLETED);
        assertThat(review.getSummary()).contains("Deterministic review");
        assertThat(review.getSeverityCounts().get("MEDIUM")).isEqualTo(1L);
        assertThat(review.getComments()).hasSize(1);
        assertThat(review.getComments().get(0).getLineNumber()).isNull();
        verify(reviewPublisher).publish(eq(review), eq("token"));
    }

    @Test
    @DisplayName("runReview() merges AI findings with deterministic findings")
    void runReview_aiFindings_mergedAndPublished() throws Exception {
        when(threadRepository.findByIdAndUserId(threadId, userId)).thenReturn(Optional.of(thread));
        when(reviewRepository.findByThreadIdAndHeadSha(threadId, "abc123")).thenReturn(Optional.empty());
        when(reviewRepository.save(any(PrReview.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenService.getInstallationToken(1L)).thenReturn("token");
        when(gitHubApiClient.compare("token", "octo", "repo", "main", "abc123"))
                .thenReturn(new GitHubCompareResponse("ahead", 1, 0, 1, List.of(),
                        List.of(new ChangedFileDto("App.java", "modified", 1, 1, "@@ -1 +1 @@\n+x"))));
        when(deterministicReviewRules.evaluate(any(), any())).thenReturn(List.of());
        when(aiReviewGenerator.generate(any(), any())).thenReturn(new AiReviewResult("AI summary",
                List.of(new ReviewFinding(FindingSeverity.HIGH, FindingCategory.BUG, "App.java", 1,
                        "Null deref", "line can be null"))));

        PrReview review = prReviewService.runReview(user, threadId, 42, "abc123", 1L);

        assertThat(review.getSummary()).isEqualTo("AI summary");
        assertThat(review.getComments()).hasSize(1);
        assertThat(review.getComments().get(0).getLineNumber()).isEqualTo(1);
        assertThat(review.getSeverityCounts().get("HIGH")).isEqualTo(1L);
        verify(reviewPublisher).publish(eq(review), eq("token"));
    }

    @Test
    @DisplayName("runReview() returns the existing review for the same head SHA (idempotent)")
    void runReview_existingReview_sameShaReturned() {
        PrReview existing = PrReview.builder().thread(thread).prNumber(42).headSha("abc123")
                .status(ReviewStatus.COMPLETED).build();
        when(threadRepository.findByIdAndUserId(threadId, userId)).thenReturn(Optional.of(thread));
        when(reviewRepository.findByThreadIdAndHeadSha(threadId, "abc123")).thenReturn(Optional.of(existing));

        PrReview review = prReviewService.runReview(user, threadId, 42, "abc123", 1L);

        assertThat(review).isSameAs(existing);
        verify(tokenService, never()).getInstallationToken(any());
        verify(gitHubApiClient, never()).compare(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("runReview() re-runs when the previous review for the SHA failed")
    void runReview_previousFailed_reruns() throws Exception {
        PrReview failed = PrReview.builder().thread(thread).prNumber(42).headSha("abc123")
                .status(ReviewStatus.FAILED).build();
        when(threadRepository.findByIdAndUserId(threadId, userId)).thenReturn(Optional.of(thread));
        when(reviewRepository.findByThreadIdAndHeadSha(threadId, "abc123")).thenReturn(Optional.of(failed));
        when(reviewRepository.save(any(PrReview.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenService.getInstallationToken(1L)).thenReturn("token");
        when(gitHubApiClient.compare("token", "octo", "repo", "main", "abc123"))
                .thenReturn(new GitHubCompareResponse("ahead", 0, 0, 0, List.of(), List.of()));
        when(deterministicReviewRules.evaluate(any(), any())).thenReturn(List.of());
        when(aiReviewGenerator.generate(any(), any())).thenReturn(null);

        PrReview review = prReviewService.runReview(user, threadId, 42, "abc123", 1L);

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.COMPLETED);
    }

    @Test
    @DisplayName("runReview() marks the review FAILED and rethrows on GitHub failure")
    void runReview_githubFailure_marksFailed() throws Exception {
        when(threadRepository.findByIdAndUserId(threadId, userId)).thenReturn(Optional.of(thread));
        when(reviewRepository.findByThreadIdAndHeadSha(threadId, "abc123")).thenReturn(Optional.empty());
        when(reviewRepository.save(any(PrReview.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenService.getInstallationToken(1L)).thenReturn("token");
        when(gitHubApiClient.compare("token", "octo", "repo", "main", "abc123"))
                .thenThrow(new RuntimeException("GitHub API error: 403"));

        assertThatThrownBy(() -> prReviewService.runReview(user, threadId, 42, "abc123", 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("GitHub API error: 403");

        ArgumentCaptor<PrReview> captor = ArgumentCaptor.forClass(PrReview.class);
        verify(reviewRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        PrReview lastSaved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(lastSaved.getStatus()).isEqualTo(ReviewStatus.FAILED);
        assertThat(lastSaved.getError()).contains("GitHub API error: 403");
    }

    @Test
    @DisplayName("runReview() throws ThreadNotFound for an unowned thread")
    void runReview_unownedThread_throws() {
        when(threadRepository.findByIdAndUserId(threadId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> prReviewService.runReview(user, threadId, 42, "abc123", 1L))
                .isInstanceOf(ThreadNotFoundException.class);
    }
}
