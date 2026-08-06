package com.devbraid.changethread.controller;

import com.devbraid.changethread.dto.request.CreateNoteRequest;
import com.devbraid.changethread.dto.request.UpdateNoteRequest;
import com.devbraid.changethread.dto.response.NoteListItemResponse;
import com.devbraid.changethread.dto.response.NoteResponse;
import com.devbraid.changethread.service.DecisionNoteService;
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

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Decision Notes", description = "Decision Notes record *why* a decision was made during a change — anchored to a commit, a file, or the thread as a whole.")
@SecurityRequirement(name = "bearer-jwt")
public class DecisionNoteController {

    private final DecisionNoteService decisionNoteService;

    @PostMapping("/api/v1/threads/{threadId}/notes")
    @Operation(
            summary = "Create a decision note",
            description = "Adds a decision note to a thread. `context` is required (`COMMIT`, `FILE` or `THREAD`); `contextRef` is required for `COMMIT` (commit sha) and `FILE` (file path) contexts."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Note created",
            content = @Content(schema = @Schema(implementation = NoteResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<NoteResponse>> createNote(
            @PathVariable @Parameter(description = "Thread ID") UUID threadId,
            @Valid @RequestBody CreateNoteRequest request,
            @AuthenticationPrincipal User user) {
        log.info("Creating note for thread {} by user {}", threadId, user.getEmail());
        NoteResponse response = decisionNoteService.createNote(user, threadId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Note created", response));
    }

    @GetMapping("/api/v1/threads/{threadId}/notes")
    @Operation(
            summary = "List a thread's decision notes",
            description = "Returns all decision notes for a thread."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Notes retrieved",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = NoteResponse.class))))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<List<NoteResponse>>> listThreadNotes(
            @PathVariable @Parameter(description = "Thread ID") UUID threadId,
            @AuthenticationPrincipal User user) {
        List<NoteResponse> notes = decisionNoteService.listNotes(threadId, user);
        return ResponseEntity.ok(ApiResponse.success("Notes retrieved", notes));
    }

    @PutMapping("/api/v1/threads/{threadId}/notes/{noteId}")
    @Operation(
            summary = "Update a decision note",
            description = "Updates the content or context of an existing decision note."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Note updated",
            content = @Content(schema = @Schema(implementation = NoteResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<NoteResponse>> updateNote(
            @PathVariable @Parameter(description = "Thread ID") UUID threadId,
            @PathVariable @Parameter(description = "Note ID") UUID noteId,
            @Valid @RequestBody UpdateNoteRequest request,
            @AuthenticationPrincipal User user) {
        log.info("Updating note {} on thread {} by user {}", noteId, threadId, user.getEmail());
        NoteResponse response = decisionNoteService.updateNote(user, noteId, request);
        return ResponseEntity.ok(ApiResponse.success("Note updated", response));
    }

    @DeleteMapping("/api/v1/threads/{threadId}/notes/{noteId}")
    @Operation(
            summary = "Delete a decision note",
            description = "Permanently removes a decision note."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Note deleted",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<Void>> deleteNote(
            @PathVariable @Parameter(description = "Thread ID") UUID threadId,
            @PathVariable @Parameter(description = "Note ID") UUID noteId,
            @AuthenticationPrincipal User user) {
        log.info("Deleting note {} on thread {} by user {}", noteId, threadId, user.getEmail());
        decisionNoteService.deleteNote(user, noteId);
        return ResponseEntity.ok(ApiResponse.success("Note deleted", null));
    }

    @GetMapping("/api/v1/notes")
    @Operation(
            summary = "List all decision notes (paginated)",
            description = "Paginated list of the authenticated user's decision notes across all threads."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Notes retrieved",
            content = @Content(schema = @Schema(implementation = Page.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<Page<NoteListItemResponse>>> listAllNotes(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user) {
        Page<NoteListItemResponse> notes = decisionNoteService.listAllNotes(user, pageable);
        return ResponseEntity.ok(ApiResponse.success("Notes retrieved", notes));
    }
}
