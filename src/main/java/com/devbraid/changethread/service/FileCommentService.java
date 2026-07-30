package com.devbraid.changethread.service;

import com.devbraid.changethread.dto.request.CreateFileCommentRequest;
import com.devbraid.changethread.dto.request.UpdateFileCommentRequest;
import com.devbraid.changethread.dto.response.FileCommentResponse;
import com.devbraid.changethread.entity.CommentStatus;
import com.devbraid.changethread.entity.FileComment;
import com.devbraid.changethread.exception.ThreadNotFoundException;
import com.devbraid.changethread.repository.ChangeThreadRepository;
import com.devbraid.changethread.repository.FileCommentRepository;
import com.devbraid.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing File Comments — inline annotations on specific files within threads.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileCommentService {

    private final FileCommentRepository commentRepository;
    private final ChangeThreadRepository threadRepository;
    private final ModelMapper generalModelMapper;

    @Transactional
    public FileCommentResponse createComment(User user, UUID threadId, CreateFileCommentRequest req) {
        var thread = threadRepository
                .findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));

        FileComment comment = FileComment.builder()
                .thread(thread)
                .author(user)
                .filePath(req.getFilePath())
                .lineStart(req.getLineStart())
                .lineEnd(req.getLineEnd())
                .content(req.getContent())
                .build();

        comment = commentRepository.save(comment);
        log.info("Created file comment {} on {} for thread {}", comment.getId(), req.getFilePath(), threadId);
        return toResponse(comment);
    }

    @Transactional(readOnly = true)
    public List<FileCommentResponse> listComments(User user, UUID threadId) {
        threadRepository.findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));

        return commentRepository.findByThreadIdOrderByCreatedAtDesc(threadId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FileCommentResponse> listCommentsByFile(User user, UUID threadId, String filePath) {
        threadRepository.findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));

        return commentRepository.findByThreadIdAndFilePathOrderByCreatedAtAsc(threadId, filePath)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public FileCommentResponse updateComment(User user, UUID threadId, UUID commentId, UpdateFileCommentRequest req) {
        threadRepository.findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));

        FileComment comment = commentRepository
                .findByIdAndAuthorId(commentId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Comment not found"));

        // Verify comment belongs to the specified thread
        if (!comment.getThread().getId().equals(threadId)) {
            throw new ThreadNotFoundException("Comment not found");
        }

        if (req.getContent() != null) {
            comment.setContent(req.getContent());
        }
        if (req.getLineStart() != null) {
            comment.setLineStart(req.getLineStart());
        }
        if (req.getLineEnd() != null) {
            comment.setLineEnd(req.getLineEnd());
        }
        if (req.getStatus() != null) {
            comment.setStatus(CommentStatus.valueOf(req.getStatus()));
        }

        comment = commentRepository.save(comment);
        return toResponse(comment);
    }

    @Transactional
    public void deleteComment(User user, UUID threadId, UUID commentId) {
        threadRepository.findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));

        FileComment comment = commentRepository
                .findByIdAndAuthorId(commentId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Comment not found"));

        // Verify comment belongs to the specified thread
        if (!comment.getThread().getId().equals(threadId)) {
            throw new ThreadNotFoundException("Comment not found");
        }

        commentRepository.delete(comment);
        log.info("Deleted file comment {} from thread {}", commentId, threadId);
    }

    // ── Private helpers ──────────────────────────────────────────────

    private FileCommentResponse toResponse(FileComment comment) {
        FileCommentResponse response = generalModelMapper.map(comment, FileCommentResponse.class);
        response.setThreadId(comment.getThread().getId());
        response.setAuthorId(comment.getAuthor().getId());
        return response;
    }
}
