package com.devbraid.review.controller;

import com.devbraid.common.ApiResponse;
import com.devbraid.common.api.ApiErrorResponses;
import com.devbraid.review.dto.response.PrReviewCommentResponse;
import com.devbraid.review.dto.response.PrReviewListItemResponse;
import com.devbraid.review.dto.response.PrReviewResponse;
import com.devbraid.review.service.PrReviewService;
import com.devbraid.review.service.PrReviewTriggerService;
import com.devbraid.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Automated PR reviews (Code-Rabbit-style). Reviews run automatically via the
 * GitHub App webhook; these endpoints surface them in the DevBraid web app and
 * allow a manual re-run.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "PR Reviews", description = "Automated PR reviews: line-level findings + summary, run by the DevBraid reviewer on GitHub pull requests.")
@SecurityRequirement(name = "bearer-jwt")
@SecurityRequirement(name = "api-key")
public class PrReviewController {

    private final PrReviewService prReviewService;
    private final PrReviewTriggerService prReviewTriggerService;

    @GetMapping("/api/v1/threads/{threadId}/review")
    @Operation(
            summary = "Get the latest review for a thread",
            description = "Returns the most recent automated review of the thread's pull request, or null if none has run yet."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Review retrieved",
            content = @Content(schema = @Schema(implementation = PrReviewResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<PrReviewResponse>> getLatestReview(
            @PathVariable @Parameter(description = "Thread ID") UUID threadId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success("Review retrieved",
                prReviewService.getLatestReview(user, threadId)));
    }

    @GetMapping("/api/v1/threads/{threadId}/review/comments")
    @Operation(
            summary = "List the latest review's findings",
            description = "Returns the findings (inline + file-level) of the thread's most recent review."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Findings retrieved",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = PrReviewCommentResponse.class))))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<List<PrReviewCommentResponse>>> getLatestReviewComments(
            @PathVariable @Parameter(description = "Thread ID") UUID threadId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success("Findings retrieved",
                prReviewService.getLatestReviewComments(user, threadId)));
    }

    @PostMapping("/api/v1/threads/{threadId}/review")
    @Operation(
            summary = "Run a review now",
            description = "Triggers an asynchronous review of the thread's pull request. Returns 202; the review is created in the background and can be polled via the GET endpoint."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Review started")
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<Void>> runReview(
            @PathVariable @Parameter(description = "Thread ID") UUID threadId,
            @RequestParam @Parameter(description = "GitHub PR number to review") int prNumber,
            @AuthenticationPrincipal User user) {
        log.info("Manual review requested for thread {} PR #{} by user {}", threadId, prNumber, user.getEmail());
        prReviewTriggerService.triggerManualReview(user, threadId, prNumber);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Review started", null));
    }

    @GetMapping("/api/v1/reviews")
    @Operation(
            summary = "List reviews (paginated)",
            description = "Paginated list of the authenticated user's automated reviews across all threads."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Reviews retrieved",
            content = @Content(schema = @Schema(implementation = Page.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<Page<PrReviewListItemResponse>>> listReviews(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success("Reviews retrieved",
                prReviewService.listReviews(user, pageable)));
    }
}
