package com.devbraid.changethread.controller;

import com.devbraid.changethread.dto.request.CreateThreadEventRequest;
import com.devbraid.changethread.dto.response.ThreadEventResponse;
import com.devbraid.changethread.service.ThreadEventService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/threads/{threadId}/events")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Thread Events", description = "Manual events attached to a change thread (e.g. 'PR merged', 'review started').")
@SecurityRequirement(name = "bearer-jwt")
@SecurityRequirement(name = "api-key")
public class ThreadEventController {

    private final ThreadEventService eventService;

    @PostMapping
    @Operation(
            summary = "Create a thread event",
            description = "Adds a manual event to the thread timeline."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Event created",
            content = @Content(schema = @Schema(implementation = ThreadEventResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<ThreadEventResponse>> createEvent(
            @PathVariable @Parameter(description = "Thread ID") UUID threadId,
            @Valid @RequestBody CreateThreadEventRequest request,
            @AuthenticationPrincipal User user) {
        log.info("Creating manual event for thread {} by user {}", threadId, user.getEmail());
        ThreadEventResponse response = eventService.createManualEvent(user, threadId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Event created", response));
    }

    @GetMapping
    @Operation(
            summary = "List a thread's events",
            description = "Returns all events for a thread."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Events retrieved",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ThreadEventResponse.class))))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<List<ThreadEventResponse>>> listEvents(
            @PathVariable @Parameter(description = "Thread ID") UUID threadId,
            @AuthenticationPrincipal User user) {
        List<ThreadEventResponse> events = eventService.listEvents(user, threadId);
        return ResponseEntity.ok(ApiResponse.success("Events retrieved", events));
    }

    @GetMapping("/paged")
    @Operation(
            summary = "List a thread's events (paginated)",
            description = "Paginated view of a thread's events."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Events retrieved",
            content = @Content(schema = @Schema(implementation = Page.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<Page<ThreadEventResponse>>> listEventsPaged(
            @PathVariable @Parameter(description = "Thread ID") UUID threadId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user) {
        Page<ThreadEventResponse> events = eventService.listEventsPaged(user, threadId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Events retrieved", events));
    }
}
