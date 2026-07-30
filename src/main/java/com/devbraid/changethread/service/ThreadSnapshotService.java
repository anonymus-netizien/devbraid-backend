package com.devbraid.changethread.service;

import com.devbraid.changethread.dto.request.CreateSnapshotRequest;
import com.devbraid.changethread.dto.response.SnapshotResponse;
import com.devbraid.changethread.entity.ChangeThread;
import com.devbraid.changethread.entity.SnapshotType;
import com.devbraid.changethread.entity.ThreadSnapshot;
import com.devbraid.changethread.exception.ThreadNotFoundException;
import com.devbraid.changethread.repository.ChangeThreadRepository;
import com.devbraid.changethread.repository.ThreadSnapshotRepository;
import com.devbraid.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing Thread Snapshots.
 * Snapshots lock the exact state of commits/files at a point in time for reproducibility.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThreadSnapshotService {

    private final ThreadSnapshotRepository snapshotRepository;
    private final ChangeThreadRepository threadRepository;

    /**
     * Create a snapshot from the current thread state.
     * Used internally when threads are created, refreshed, or analyzed.
     */
    @Transactional
    public ThreadSnapshot createSnapshot(ChangeThread thread, User user, SnapshotType type, String note) {
        ThreadSnapshot snapshot = ThreadSnapshot.builder()
                .thread(thread)
                .user(user)
                .repositoryFullName(thread.getRepositoryFullName())
                .headBranch(thread.getHeadBranch())
                .baseBranch(thread.getBaseBranch())
                .commitSha(thread.getCommitSha())
                .commits(thread.getCommits())
                .changedFiles(thread.getChangedFiles())
                .title(thread.getTitle())
                .description(thread.getDescription())
                .type(type)
                .note(note)
                .build();

        snapshot = snapshotRepository.save(snapshot);
        log.info("Created {} snapshot {} for thread {}", type, snapshot.getId(), thread.getId());
        return snapshot;
    }

    /**
     * Create a manual snapshot with a user-provided note.
     */
    @Transactional
    public SnapshotResponse createManualSnapshot(User user, UUID threadId, CreateSnapshotRequest req) {
        ChangeThread thread = threadRepository
                .findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));

        ThreadSnapshot snapshot = createSnapshot(thread, user, SnapshotType.MANUAL, req.getNote());
        return toResponse(snapshot);
    }

    /**
     * List all snapshots for a thread, ordered by creation time descending.
     */
    @Transactional(readOnly = true)
    public List<SnapshotResponse> listSnapshots(User user, UUID threadId) {
        // Verify ownership
        threadRepository.findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));

        return snapshotRepository.findByThreadIdOrderByCreatedAtDesc(threadId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Get the most recent snapshot for a thread.
     */
    @Transactional(readOnly = true)
    public SnapshotResponse getLatestSnapshot(User user, UUID threadId) {
        threadRepository.findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));

        ThreadSnapshot snapshot = snapshotRepository
                .findFirstByThreadIdOrderByCreatedAtDesc(threadId)
                .orElse(null);

        return snapshot != null ? toResponse(snapshot) : null;
    }

    /**
     * Get a specific snapshot by ID.
     */
    @Transactional(readOnly = true)
    public SnapshotResponse getSnapshot(User user, UUID threadId, UUID snapshotId) {
        threadRepository.findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));

        ThreadSnapshot snapshot = snapshotRepository
                .findByIdAndUserId(snapshotId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Snapshot not found"));

        // Verify snapshot belongs to the specified thread
        if (!snapshot.getThread().getId().equals(threadId)) {
            throw new ThreadNotFoundException("Snapshot not found");
        }

        return toResponse(snapshot);
    }

    // ── Private helpers ──────────────────────────────────────────────

    private SnapshotResponse toResponse(ThreadSnapshot snapshot) {
        return SnapshotResponse.builder()
                .id(snapshot.getId())
                .threadId(snapshot.getThread().getId())
                .userId(snapshot.getUser().getId())
                .repositoryFullName(snapshot.getRepositoryFullName())
                .headBranch(snapshot.getHeadBranch())
                .baseBranch(snapshot.getBaseBranch())
                .commitSha(snapshot.getCommitSha())
                .commits(snapshot.getCommits())
                .changedFiles(snapshot.getChangedFiles())
                .title(snapshot.getTitle())
                .description(snapshot.getDescription())
                .type(snapshot.getType())
                .note(snapshot.getNote())
                .createdAt(snapshot.getCreatedAt())
                .build();
    }
}
