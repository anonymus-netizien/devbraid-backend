package com.devbraid.changethread.controller;

import com.devbraid.changethread.dto.request.CreateThreadEventRequest;
import com.devbraid.changethread.dto.response.ThreadEventResponse;
import com.devbraid.changethread.service.ThreadEventService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/threads/{threadId}/events")
@RequiredArgsConstructor
@Slf4j
public class ThreadEventController {

    private final ThreadEventService eventService;

    @PostMapping
    public ResponseEntity<ApiResponse<ThreadEventResponse>> createEvent(
            @PathVariable UUID threadId,
            @Valid @RequestBody CreateThreadEventRequest request,
            @AuthenticationPrincipal User user) {
        log.info("Creating manual event for thread {} by user {}", threadId, user.getEmail());
        ThreadEventResponse response = eventService.createManualEvent(user, threadId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Event created", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ThreadEventResponse>>> listEvents(
            @PathVariable UUID threadId,
            @AuthenticationPrincipal User user) {
        List<ThreadEventResponse> events = eventService.listEvents(user, threadId);
        return ResponseEntity.ok(ApiResponse.success("Events retrieved", events));
    }

    @GetMapping("/paged")
    public ResponseEntity<ApiResponse<Page<ThreadEventResponse>>> listEventsPaged(
            @PathVariable UUID threadId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user) {
        Page<ThreadEventResponse> events = eventService.listEventsPaged(user, threadId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Events retrieved", events));
    }
}
