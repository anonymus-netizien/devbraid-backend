package com.devbraid.changethread.controller;

import com.devbraid.brief.dto.BriefResponse;
import com.devbraid.brief.dto.PublishResponse;
import com.devbraid.brief.service.BriefBuilderService;
import com.devbraid.brief.service.BriefPublisherService;
import com.devbraid.changethread.dto.request.CreateThreadRequest;
import com.devbraid.changethread.dto.request.UpdateThreadRequest;
import com.devbraid.changethread.dto.response.ThreadResponse;
import com.devbraid.changethread.service.ChangeThreadService;
import com.devbraid.common.ApiResponse;
import com.devbraid.common.api.ApiErrorResponses;
import com.devbraid.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/threads")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Change Threads", description = "Change Threads capture the *why* behind a code change: a branch pair, the commits/diffs involved, risk analysis and the resulting change brief.")
@SecurityRequirement(name = "bearer-jwt")
public class ChangeThreadController {

    private final ChangeThreadService threadService;
    private final BriefBuilderService briefBuilderService;
    private final BriefPublisherService briefPublisherService;

    @PostMapping
    @Operation(
            summary = "Create a change thread",
            description = "Creates a Change Thread for the given repository and branch pair, fetching the commits/diff between the base and head branches."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Thread created",
            content = @Content(schema = @Schema(implementation = ThreadResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<ThreadResponse>> createThread(
            @Valid @RequestBody CreateThreadRequest request,
            @AuthenticationPrincipal User user) throws Exception {
        log.info("Creating thread for user {} on {}", user.getEmail(), request.getRepositoryFullName());
        ThreadResponse response = threadService.createThread(user, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Thread created", response));
    }

    @GetMapping
    @Operation(
            summary = "List change threads",
            description = "Paginated list of the authenticated user's change threads, newest first."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Threads retrieved",
            content = @Content(schema = @Schema(implementation = Page.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<Page<ThreadResponse>>> listThreads(
            @PageableDefault(size = 20) @Parameter(description = "Spring-style paging: `page`, `size`, `sort`") Pageable pageable,
            @AuthenticationPrincipal User user) {
        Page<ThreadResponse> threads = threadService.listThreads(user, pageable);
        return ResponseEntity.ok(ApiResponse.success("Threads retrieved", threads));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get a change thread",
            description = "Returns a single thread with its metadata, commit summaries and risk flags."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Thread retrieved",
            content = @Content(schema = @Schema(implementation = ThreadResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<ThreadResponse>> getThread(
            @PathVariable @Parameter(description = "Thread ID") UUID id,
            @AuthenticationPrincipal User user) {
        ThreadResponse response = threadService.getThread(user, id);
        return ResponseEntity.ok(ApiResponse.success("Thread retrieved", response));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update a change thread",
            description = "Updates thread metadata (title, description, branch pairing)."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Thread updated",
            content = @Content(schema = @Schema(implementation = ThreadResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<ThreadResponse>> updateThread(
            @PathVariable @Parameter(description = "Thread ID") UUID id,
            @Valid @RequestBody UpdateThreadRequest request,
            @AuthenticationPrincipal User user) {
        log.info("Updating thread {} for user {}", id, user.getEmail());
        ThreadResponse response = threadService.updateThread(user, id, request);
        return ResponseEntity.ok(ApiResponse.success("Thread updated", response));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a change thread",
            description = "Permanently deletes the thread and its related notes, briefs, comments and snapshots."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Thread deleted",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<Void>> deleteThread(
            @PathVariable @Parameter(description = "Thread ID") UUID id,
            @AuthenticationPrincipal User user) {
        log.info("Deleting thread {} for user {}", id, user.getEmail());
        threadService.deleteThread(user, id);
        return ResponseEntity.ok(ApiResponse.success("Thread deleted", null));
    }

    @PostMapping("/{id}/refresh")
    @Operation(
            summary = "Refresh a change thread",
            description = "Re-pulls the latest commits/diff from GitHub for the thread's branch pair and updates the thread."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Thread refreshed",
            content = @Content(schema = @Schema(implementation = ThreadResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<ThreadResponse>> refreshThread(
            @PathVariable @Parameter(description = "Thread ID") UUID id,
            @AuthenticationPrincipal User user) throws Exception {
        log.info("Refreshing thread {} for user {}", id, user.getEmail());
        ThreadResponse response = threadService.refreshThread(user, id);
        return ResponseEntity.ok(ApiResponse.success("Thread refreshed", response));
    }

    @PostMapping("/{id}/analyze")
    @Operation(
            summary = "Analyze a change thread",
            description = "Runs deterministic risk-flag rules and AI analysis over the thread's diff, producing risk flags and a summary."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Thread analyzed",
            content = @Content(schema = @Schema(implementation = ThreadResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<ThreadResponse>> analyzeThread(
            @PathVariable @Parameter(description = "Thread ID") UUID id,
            @AuthenticationPrincipal User user) throws Exception {
        log.info("Analyzing thread {} for user {}", id, user.getEmail());
        ThreadResponse response = threadService.analyzeThread(user, id);
        return ResponseEntity.ok(ApiResponse.success("Thread analyzed", response));
    }

    @PostMapping("/{id}/brief")
    @Operation(
            summary = "Generate a change brief",
            description = "Builds a Markdown change brief from the thread's analysis (AI model or template fallback when no key is configured)."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Brief generated",
            content = @Content(schema = @Schema(implementation = BriefResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<BriefResponse>> generateBrief(
            @PathVariable @Parameter(description = "Thread ID") UUID id,
            @AuthenticationPrincipal User user) throws Exception {
        log.info("Generating brief for thread {} by user {}", id, user.getEmail());
        BriefResponse response = briefBuilderService.generateBrief(user, id);
        return ResponseEntity.ok(ApiResponse.success("Brief generated", response));
    }

    @GetMapping("/{id}/brief")
    @Operation(
            summary = "Get a change brief",
            description = "Returns the generated brief for the thread, if one exists."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Brief retrieved",
            content = @Content(schema = @Schema(implementation = BriefResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<BriefResponse>> getBrief(
            @PathVariable @Parameter(description = "Thread ID") UUID id,
            @AuthenticationPrincipal User user) {
        BriefResponse response = briefBuilderService.getBrief(user, id);
        return ResponseEntity.ok(ApiResponse.success("Brief retrieved", response));
    }

    @PostMapping("/{id}/publish")
    @Operation(
            summary = "Publish brief to GitHub PR",
            description = "Posts the change brief as a comment on the given pull request of the thread's repository. Requires the connected PAT to have PR-comment scope."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Publish result",
            content = @Content(schema = @Schema(implementation = PublishResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<PublishResponse>> publishBrief(
            @PathVariable @Parameter(description = "Thread ID") UUID id,
            @RequestParam(name = "prNumber") @Parameter(description = "Pull request number", example = "42") int prNumber,
            @AuthenticationPrincipal User user) {
        log.info("Publishing brief for thread {} to PR #{} by user {}", id, prNumber, user.getEmail());
        PublishResponse response = briefPublisherService.publishToGitHub(user, id, prNumber);
        return ResponseEntity.ok(ApiResponse.success("Publish result", response));
    }
}
