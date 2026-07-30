package com.devbraid.changethread.controller;

import com.devbraid.brief.dto.BriefResponse;
import com.devbraid.brief.dto.PublishResponse;
import com.devbraid.brief.service.BriefBuilderService;
import com.devbraid.brief.service.BriefPublisherService;
import com.devbraid.changethread.dto.request.CreateThreadRequest;
import com.devbraid.changethread.dto.request.UpdateThreadRequest;
import com.devbraid.changethread.dto.response.ThreadResponse;
import com.devbraid.changethread.service.ChangeThreadService;
import com.devbraid.changethread.service.ThreadSearchService;
import com.devbraid.common.ApiResponse;
import com.devbraid.user.entity.User;
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
public class ChangeThreadController {

    private final ChangeThreadService threadService;
    private final BriefBuilderService briefBuilderService;
    private final BriefPublisherService briefPublisherService;
    private final ThreadSearchService searchService;

    @PostMapping
    public ResponseEntity<ApiResponse<ThreadResponse>> createThread(
            @Valid @RequestBody CreateThreadRequest request,
            @AuthenticationPrincipal User user) throws Exception {
        log.info("Creating thread for user {} on {}", user.getEmail(), request.getRepositoryFullName());
        ThreadResponse response = threadService.createThread(user, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Thread created", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ThreadResponse>>> listThreads(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user) {
        Page<ThreadResponse> threads = threadService.listThreads(user, pageable);
        return ResponseEntity.ok(ApiResponse.success("Threads retrieved", threads));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<ThreadResponse>>> searchThreads(
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user) {
        Page<ThreadResponse> results = searchService.searchThreads(user, q, pageable);
        return ResponseEntity.ok(ApiResponse.success("Search results", results));
    }

    @GetMapping("/search/status")
    public ResponseEntity<ApiResponse<Page<ThreadResponse>>> searchByStatus(
            @RequestParam com.devbraid.changethread.entity.ThreadStatus status,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user) {
        Page<ThreadResponse> results = searchService.searchByStatus(user, status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Search results", results));
    }

    @GetMapping("/search/repo")
    public ResponseEntity<ApiResponse<Page<ThreadResponse>>> searchByRepo(
            @RequestParam String repositoryFullName,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user) {
        Page<ThreadResponse> results = searchService.searchByRepository(user, repositoryFullName, pageable);
        return ResponseEntity.ok(ApiResponse.success("Search results", results));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ThreadResponse>> getThread(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        ThreadResponse response = threadService.getThread(user, id);
        return ResponseEntity.ok(ApiResponse.success("Thread retrieved", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ThreadResponse>> updateThread(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateThreadRequest request,
            @AuthenticationPrincipal User user) {
        log.info("Updating thread {} for user {}", id, user.getEmail());
        ThreadResponse response = threadService.updateThread(user, id, request);
        return ResponseEntity.ok(ApiResponse.success("Thread updated", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteThread(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        log.info("Deleting thread {} for user {}", id, user.getEmail());
        threadService.deleteThread(user, id);
        return ResponseEntity.ok(ApiResponse.success("Thread deleted", null));
    }

    @PostMapping("/{id}/refresh")
    public ResponseEntity<ApiResponse<ThreadResponse>> refreshThread(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) throws Exception {
        log.info("Refreshing thread {} for user {}", id, user.getEmail());
        ThreadResponse response = threadService.refreshThread(user, id);
        return ResponseEntity.ok(ApiResponse.success("Thread refreshed", response));
    }

    @PostMapping("/{id}/analyze")
    public ResponseEntity<ApiResponse<ThreadResponse>> analyzeThread(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) throws Exception {
        log.info("Analyzing thread {} for user {}", id, user.getEmail());
        ThreadResponse response = threadService.analyzeThread(user, id);
        return ResponseEntity.ok(ApiResponse.success("Thread analyzed", response));
    }

    @PostMapping("/{id}/brief")
    public ResponseEntity<ApiResponse<BriefResponse>> generateBrief(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) throws Exception {
        log.info("Generating brief for thread {} by user {}", id, user.getEmail());
        BriefResponse response = briefBuilderService.generateBrief(user, id);
        return ResponseEntity.ok(ApiResponse.success("Brief generated", response));
    }

    @GetMapping("/{id}/brief")
    public ResponseEntity<ApiResponse<BriefResponse>> getBrief(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        BriefResponse response = briefBuilderService.getBrief(user, id);
        return ResponseEntity.ok(ApiResponse.success("Brief retrieved", response));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<PublishResponse>> publishBrief(
            @PathVariable UUID id,
            @RequestParam(name = "prNumber") int prNumber,
            @AuthenticationPrincipal User user) {
        log.info("Publishing brief for thread {} to PR #{} by user {}", id, prNumber, user.getEmail());
        PublishResponse response = briefPublisherService.publishToGitHub(user, id, prNumber);
        return ResponseEntity.ok(ApiResponse.success("Publish result", response));
    }
}
