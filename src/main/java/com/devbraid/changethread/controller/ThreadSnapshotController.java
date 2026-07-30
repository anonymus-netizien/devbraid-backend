package com.devbraid.changethread.controller;

import com.devbraid.changethread.dto.request.CreateSnapshotRequest;
import com.devbraid.changethread.dto.response.SnapshotResponse;
import com.devbraid.changethread.service.ThreadSnapshotService;
import com.devbraid.common.ApiResponse;
import com.devbraid.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/threads/{threadId}/snapshots")
@RequiredArgsConstructor
@Slf4j
public class ThreadSnapshotController {

    private final ThreadSnapshotService snapshotService;

    @PostMapping
    public ResponseEntity<ApiResponse<SnapshotResponse>> createSnapshot(
            @PathVariable UUID threadId,
            @Valid @RequestBody CreateSnapshotRequest request,
            @AuthenticationPrincipal User user) {
        log.info("Creating manual snapshot for thread {} by user {}", threadId, user.getEmail());
        SnapshotResponse response = snapshotService.createManualSnapshot(user, threadId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Snapshot created", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SnapshotResponse>>> listSnapshots(
            @PathVariable UUID threadId,
            @AuthenticationPrincipal User user) {
        List<SnapshotResponse> snapshots = snapshotService.listSnapshots(user, threadId);
        return ResponseEntity.ok(ApiResponse.success("Snapshots retrieved", snapshots));
    }

    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<SnapshotResponse>> getLatestSnapshot(
            @PathVariable UUID threadId,
            @AuthenticationPrincipal User user) {
        SnapshotResponse response = snapshotService.getLatestSnapshot(user, threadId);
        return ResponseEntity.ok(ApiResponse.success("Latest snapshot retrieved", response));
    }

    @GetMapping("/{snapshotId}")
    public ResponseEntity<ApiResponse<SnapshotResponse>> getSnapshot(
            @PathVariable UUID threadId,
            @PathVariable UUID snapshotId,
            @AuthenticationPrincipal User user) {
        SnapshotResponse response = snapshotService.getSnapshot(user, threadId, snapshotId);
        return ResponseEntity.ok(ApiResponse.success("Snapshot retrieved", response));
    }
}
