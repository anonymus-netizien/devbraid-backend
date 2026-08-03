package com.devbraid.changethread.controller;

import com.devbraid.changethread.dto.request.CreateSnapshotRequest;
import com.devbraid.changethread.dto.response.SnapshotResponse;
import com.devbraid.changethread.service.ThreadSnapshotService;
import com.devbraid.common.ApiResponse;
import com.devbraid.common.api.ApiErrorResponses;
import com.devbraid.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Thread Snapshots", description = "Point-in-time snapshots of a thread's state (manual or auto-captured).")
@SecurityRequirement(name = "bearer-jwt")
@SecurityRequirement(name = "api-key")
public class ThreadSnapshotController {

    private final ThreadSnapshotService snapshotService;

    @PostMapping
    @Operation(
            summary = "Create a snapshot",
            description = "Captures a manual snapshot of the thread at its current state."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Snapshot created",
            content = @Content(schema = @Schema(implementation = SnapshotResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<SnapshotResponse>> createSnapshot(
            @PathVariable @Parameter(description = "Thread ID") UUID threadId,
            @Valid @RequestBody CreateSnapshotRequest request,
            @AuthenticationPrincipal User user) {
        log.info("Creating manual snapshot for thread {} by user {}", threadId, user.getEmail());
        SnapshotResponse response = snapshotService.createManualSnapshot(user, threadId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Snapshot created", response));
    }

    @GetMapping
    @Operation(
            summary = "List a thread's snapshots",
            description = "Returns all snapshots for a thread."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Snapshots retrieved",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = SnapshotResponse.class))))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<List<SnapshotResponse>>> listSnapshots(
            @PathVariable @Parameter(description = "Thread ID") UUID threadId,
            @AuthenticationPrincipal User user) {
        List<SnapshotResponse> snapshots = snapshotService.listSnapshots(user, threadId);
        return ResponseEntity.ok(ApiResponse.success("Snapshots retrieved", snapshots));
    }

    @GetMapping("/latest")
    @Operation(
            summary = "Get the latest snapshot",
            description = "Returns the most recent snapshot of the thread, if one exists."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Latest snapshot retrieved",
            content = @Content(schema = @Schema(implementation = SnapshotResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<SnapshotResponse>> getLatestSnapshot(
            @PathVariable @Parameter(description = "Thread ID") UUID threadId,
            @AuthenticationPrincipal User user) {
        SnapshotResponse response = snapshotService.getLatestSnapshot(user, threadId);
        return ResponseEntity.ok(ApiResponse.success("Latest snapshot retrieved", response));
    }

    @GetMapping("/{snapshotId}")
    @Operation(
            summary = "Get a snapshot by ID",
            description = "Returns a specific snapshot of the thread."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Snapshot retrieved",
            content = @Content(schema = @Schema(implementation = SnapshotResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<SnapshotResponse>> getSnapshot(
            @PathVariable @Parameter(description = "Thread ID") UUID threadId,
            @PathVariable @Parameter(description = "Snapshot ID") UUID snapshotId,
            @AuthenticationPrincipal User user) {
        SnapshotResponse response = snapshotService.getSnapshot(user, threadId, snapshotId);
        return ResponseEntity.ok(ApiResponse.success("Snapshot retrieved", response));
    }
}
