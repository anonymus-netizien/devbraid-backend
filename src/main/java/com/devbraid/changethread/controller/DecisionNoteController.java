package com.devbraid.changethread.controller;

import com.devbraid.changethread.dto.request.CreateNoteRequest;
import com.devbraid.changethread.dto.request.UpdateNoteRequest;
import com.devbraid.changethread.dto.response.NoteListItemResponse;
import com.devbraid.changethread.dto.response.NoteResponse;
import com.devbraid.changethread.exception.ThreadNotFoundException;
import com.devbraid.changethread.repository.ChangeThreadRepository;
import com.devbraid.changethread.service.DecisionNoteService;
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

@Slf4j
@RestController
@RequiredArgsConstructor
public class DecisionNoteController {

    private final DecisionNoteService decisionNoteService;
    private final ChangeThreadRepository threadRepository;

    @PostMapping("/api/v1/threads/{threadId}/notes")
    public ResponseEntity<ApiResponse<NoteResponse>> createNote(
            @PathVariable UUID threadId,
            @Valid @RequestBody CreateNoteRequest request,
            @AuthenticationPrincipal User user) {
        log.info("Creating note for thread {} by user {}", threadId, user.getEmail());
        NoteResponse response = decisionNoteService.createNote(user, threadId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Note created", response));
    }

    @GetMapping("/api/v1/threads/{threadId}/notes")
    public ResponseEntity<ApiResponse<List<NoteResponse>>> listThreadNotes(
            @PathVariable UUID threadId,
            @AuthenticationPrincipal User user) {
        // Verify thread ownership before listing notes
        threadRepository.findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));
        List<NoteResponse> notes = decisionNoteService.listNotes(threadId);
        return ResponseEntity.ok(ApiResponse.success("Notes retrieved", notes));
    }

    @PutMapping("/api/v1/threads/{threadId}/notes/{noteId}")
    public ResponseEntity<ApiResponse<NoteResponse>> updateNote(
            @PathVariable UUID threadId,
            @PathVariable UUID noteId,
            @Valid @RequestBody UpdateNoteRequest request,
            @AuthenticationPrincipal User user) {
        log.info("Updating note {} on thread {} by user {}", noteId, threadId, user.getEmail());
        NoteResponse response = decisionNoteService.updateNote(user, noteId, request);
        return ResponseEntity.ok(ApiResponse.success("Note updated", response));
    }

    @DeleteMapping("/api/v1/threads/{threadId}/notes/{noteId}")
    public ResponseEntity<ApiResponse<Void>> deleteNote(
            @PathVariable UUID threadId,
            @PathVariable UUID noteId,
            @AuthenticationPrincipal User user) {
        log.info("Deleting note {} on thread {} by user {}", noteId, threadId, user.getEmail());
        decisionNoteService.deleteNote(user, noteId);
        return ResponseEntity.ok(ApiResponse.success("Note deleted", null));
    }

    @GetMapping("/api/v1/notes")
    public ResponseEntity<ApiResponse<Page<NoteListItemResponse>>> listAllNotes(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user) {
        Page<NoteListItemResponse> notes = decisionNoteService.listAllNotes(user, pageable);
        return ResponseEntity.ok(ApiResponse.success("Notes retrieved", notes));
    }
}
