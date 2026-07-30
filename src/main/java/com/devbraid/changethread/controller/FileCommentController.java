package com.devbraid.changethread.controller;

import com.devbraid.changethread.dto.request.CreateFileCommentRequest;
import com.devbraid.changethread.dto.request.UpdateFileCommentRequest;
import com.devbraid.changethread.dto.response.FileCommentResponse;
import com.devbraid.changethread.service.FileCommentService;
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
@RequestMapping("/api/v1/threads/{threadId}/comments")
@RequiredArgsConstructor
@Slf4j
public class FileCommentController {

    private final FileCommentService commentService;

    @PostMapping
    public ResponseEntity<ApiResponse<FileCommentResponse>> createComment(
            @PathVariable UUID threadId,
            @Valid @RequestBody CreateFileCommentRequest request,
            @AuthenticationPrincipal User user) {
        log.info("Creating file comment on {} for thread {} by user {}", request.getFilePath(), threadId, user.getEmail());
        FileCommentResponse response = commentService.createComment(user, threadId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Comment created", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FileCommentResponse>>> listComments(
            @PathVariable UUID threadId,
            @AuthenticationPrincipal User user) {
        List<FileCommentResponse> comments = commentService.listComments(user, threadId);
        return ResponseEntity.ok(ApiResponse.success("Comments retrieved", comments));
    }

    @GetMapping("/by-file")
    public ResponseEntity<ApiResponse<List<FileCommentResponse>>> listCommentsByFile(
            @PathVariable UUID threadId,
            @RequestParam String filePath,
            @AuthenticationPrincipal User user) {
        List<FileCommentResponse> comments = commentService.listCommentsByFile(user, threadId, filePath);
        return ResponseEntity.ok(ApiResponse.success("Comments retrieved", comments));
    }

    @PutMapping("/{commentId}")
    public ResponseEntity<ApiResponse<FileCommentResponse>> updateComment(
            @PathVariable UUID threadId,
            @PathVariable UUID commentId,
            @Valid @RequestBody UpdateFileCommentRequest request,
            @AuthenticationPrincipal User user) {
        log.info("Updating file comment {} for thread {} by user {}", commentId, threadId, user.getEmail());
        FileCommentResponse response = commentService.updateComment(user, threadId, commentId, request);
        return ResponseEntity.ok(ApiResponse.success("Comment updated", response));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable UUID threadId,
            @PathVariable UUID commentId,
            @AuthenticationPrincipal User user) {
        log.info("Deleting file comment {} from thread {} by user {}", commentId, threadId, user.getEmail());
        commentService.deleteComment(user, threadId, commentId);
        return ResponseEntity.ok(ApiResponse.success("Comment deleted", null));
    }
}
