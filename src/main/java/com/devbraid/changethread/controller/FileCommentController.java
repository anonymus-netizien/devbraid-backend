package com.devbraid.changethread.controller;

import com.devbraid.changethread.dto.request.CreateFileCommentRequest;
import com.devbraid.changethread.dto.request.UpdateFileCommentRequest;
import com.devbraid.changethread.dto.response.FileCommentResponse;
import com.devbraid.changethread.service.FileCommentService;
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
@RequestMapping("/api/v1/threads/{threadId}/comments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "File Comments", description = "Inline comments anchored to a specific file path within a change thread.")
@SecurityRequirement(name = "bearer-jwt")
@SecurityRequirement(name = "api-key")
public class FileCommentController {

    private final FileCommentService commentService;

    @PostMapping
    @Operation(
            summary = "Create a file comment",
            description = "Adds a comment anchored to a file path (and optionally a line) in the thread."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Comment created",
            content = @Content(schema = @Schema(implementation = FileCommentResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<FileCommentResponse>> createComment(
            @PathVariable @Parameter(description = "Thread ID") UUID threadId,
            @Valid @RequestBody CreateFileCommentRequest request,
            @AuthenticationPrincipal User user) {
        log.info("Creating file comment on {} for thread {} by user {}", request.getFilePath(), threadId, user.getEmail());
        FileCommentResponse response = commentService.createComment(user, threadId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Comment created", response));
    }

    @GetMapping
    @Operation(
            summary = "List a thread's file comments",
            description = "Returns all file comments for a thread."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Comments retrieved",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = FileCommentResponse.class))))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<List<FileCommentResponse>>> listComments(
            @PathVariable @Parameter(description = "Thread ID") UUID threadId,
            @AuthenticationPrincipal User user) {
        List<FileCommentResponse> comments = commentService.listComments(user, threadId);
        return ResponseEntity.ok(ApiResponse.success("Comments retrieved", comments));
    }

    @GetMapping("/by-file")
    @Operation(
            summary = "List file comments for one file",
            description = "Returns the thread's comments anchored to a specific file path."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Comments retrieved",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = FileCommentResponse.class))))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<List<FileCommentResponse>>> listCommentsByFile(
            @PathVariable @Parameter(description = "Thread ID") UUID threadId,
            @RequestParam @Parameter(description = "File path", example = "src/main/java/com/devbraid/security/JwtTokenProvider.java") String filePath,
            @AuthenticationPrincipal User user) {
        List<FileCommentResponse> comments = commentService.listCommentsByFile(user, threadId, filePath);
        return ResponseEntity.ok(ApiResponse.success("Comments retrieved", comments));
    }

    @PutMapping("/{commentId}")
    @Operation(
            summary = "Update a file comment",
            description = "Updates the content of a file comment."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Comment updated",
            content = @Content(schema = @Schema(implementation = FileCommentResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<FileCommentResponse>> updateComment(
            @PathVariable @Parameter(description = "Thread ID") UUID threadId,
            @PathVariable @Parameter(description = "Comment ID") UUID commentId,
            @Valid @RequestBody UpdateFileCommentRequest request,
            @AuthenticationPrincipal User user) {
        log.info("Updating file comment {} for thread {} by user {}", commentId, threadId, user.getEmail());
        FileCommentResponse response = commentService.updateComment(user, threadId, commentId, request);
        return ResponseEntity.ok(ApiResponse.success("Comment updated", response));
    }

    @DeleteMapping("/{commentId}")
    @Operation(
            summary = "Delete a file comment",
            description = "Permanently removes a file comment."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Comment deleted",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable @Parameter(description = "Thread ID") UUID threadId,
            @PathVariable @Parameter(description = "Comment ID") UUID commentId,
            @AuthenticationPrincipal User user) {
        log.info("Deleting file comment {} from thread {} by user {}", commentId, threadId, user.getEmail());
        commentService.deleteComment(user, threadId, commentId);
        return ResponseEntity.ok(ApiResponse.success("Comment deleted", null));
    }
}
