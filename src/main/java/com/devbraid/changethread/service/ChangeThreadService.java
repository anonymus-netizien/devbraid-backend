package com.devbraid.changethread.service;

import com.devbraid.analysis.service.RiskAnalysisService;
import com.devbraid.changethread.dto.request.CreateThreadRequest;
import com.devbraid.changethread.dto.request.UpdateThreadRequest;
import com.devbraid.changethread.dto.response.NoteResponse;
import com.devbraid.changethread.dto.response.ThreadResponse;
import com.devbraid.changethread.entity.ChangeThread;
import com.devbraid.changethread.entity.DecisionNote;
import com.devbraid.analysis.RiskLevel;
import com.devbraid.changethread.exception.ThreadNotFoundException;
import com.devbraid.changethread.repository.ChangeThreadRepository;
import com.devbraid.changethread.repository.DecisionNoteRepository;
import com.devbraid.github.client.GitHubApiClient;
import com.devbraid.github.dto.response.GitHubCompareResponse;
import com.devbraid.github.service.GitHubConnectionService;

import com.devbraid.user.entity.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing Change Threads.
 * Handles CRUD operations, GitHub diff fetching, and thread lifecycle.
 * <p>
 * All exceptions propagate to GlobalExceptionHandler — no try-catches here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeThreadService {

    private final ChangeThreadRepository threadRepository;
    private final DecisionNoteRepository noteRepository;
    private final GitHubConnectionService gitHubConnectionService;
    private final GitHubApiClient gitHubApiClient;
    private final ObjectMapper objectMapper;
    private final RiskAnalysisService riskAnalysisService;
    private final ThreadSnapshotService snapshotService;
    private final ThreadEventService eventService;
    private final ModelMapper generalModelMapper;

    /**
     * Create a new Change Thread by fetching diff data from GitHub.
     *
     * @param user the authenticated user
     * @param req  thread creation request
     * @return created thread response
     * @throws GitHubNotConnectedException if user has no GitHub connection
     */
    @Transactional(rollbackFor = JsonProcessingException.class)
    public ThreadResponse createThread(User user, CreateThreadRequest req) throws JsonProcessingException {
        String decryptedPat = gitHubConnectionService.getDecryptedPatForUser(user);

        String[] parts = req.getRepositoryFullName().split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid repository format. Use 'owner/repo'");
        }
        String owner = parts[0];
        String repo = parts[1];

        String baseBranch = req.getBaseBranch();
        if (baseBranch == null || baseBranch.isBlank()) {
            baseBranch = "main";
        }

        // Fetch diff from GitHub — single branch vs cross-branch compare
        String commitsJson = null;
        String changedFilesJson = null;
        String latestCommitSha = null;

        if (req.getHeadBranch().equalsIgnoreCase(baseBranch)) {
            // Single-Branch Mode: fetch recent commits directly for this branch
            List<com.devbraid.github.dto.response.CommitSummaryDto> branchCommits =
                    gitHubApiClient.listCommits(decryptedPat, owner, repo, req.getHeadBranch(), 20);
            if (branchCommits != null && !branchCommits.isEmpty()) {
                commitsJson = serializeToJson(branchCommits);
                latestCommitSha = branchCommits.get(0).getSha();
            }
        } else {
            // Cross-Branch Compare Mode
            GitHubCompareResponse compare = gitHubApiClient.compare(
                    decryptedPat, owner, repo, baseBranch, req.getHeadBranch()
            );
            if (compare != null) {
                commitsJson = serializeToJson(compare.getCommits());
                changedFilesJson = serializeToJson(compare.getFiles());
                if (compare.getCommits() != null && !compare.getCommits().isEmpty()) {
                    latestCommitSha = compare.getCommits()
                            .get(compare.getCommits().size() - 1).getSha();
                }
            }
        }

        ChangeThread thread = ChangeThread.builder()
                .user(user)
                .repositoryFullName(req.getRepositoryFullName())
                .headBranch(req.getHeadBranch())
                .baseBranch(baseBranch)
                .title(req.getTitle())
                .description(req.getDescription())
                .commitSha(latestCommitSha)
                .commits(commitsJson)
                .changedFiles(changedFilesJson)
                .build();

        thread = threadRepository.save(thread);
        log.info("Created thread {} for user {} on {}/{}", thread.getId(), user.getId(), owner, repo);

        // Create initial snapshot and timeline event
        snapshotService.createSnapshot(thread, user, com.devbraid.changethread.entity.SnapshotType.CREATION, null);
        eventService.recordEvent(thread, user, com.devbraid.changethread.entity.ThreadEventType.THREAD_CREATED,
                String.format("Thread '%s' created for %s (%s → %s)", thread.getTitle(), thread.getRepositoryFullName(), thread.getBaseBranch(), thread.getHeadBranch()),
                null);

        return toResponse(thread);
    }

    @Transactional(readOnly = true)
    public Page<ThreadResponse> listThreads(User user, Pageable pageable) {
        Page<ChangeThread> threadPage = threadRepository
                .findAllByUserIdOrderByCreatedAtDesc(user.getId(), pageable);

        // Batch-fetch notes for all threads in one query — eliminates N+1
        return toResponsePage(threadPage);
    }

    @Transactional(readOnly = true)
    public ThreadResponse getThread(User user, UUID threadId) {
        ChangeThread thread = threadRepository
                .findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));
        return toResponse(thread);
    }

    @Transactional
    public ThreadResponse updateThread(User user, UUID threadId, UpdateThreadRequest req) {
        ChangeThread thread = threadRepository
                .findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));

        if (req.getTitle() != null) {
            thread.setTitle(req.getTitle());
        }
        if (req.getDescription() != null) {
            thread.setDescription(req.getDescription());
        }

        thread = threadRepository.save(thread);

        eventService.recordEvent(thread, user, com.devbraid.changethread.entity.ThreadEventType.STATUS_CHANGED,
                String.format("Thread updated: %s", thread.getTitle()), null);

        return toResponse(thread);
    }

    @Transactional
    public void deleteThread(User user, UUID threadId) {
        ChangeThread thread = threadRepository
                .findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));

        threadRepository.delete(thread);
        log.info("Deleted thread {} for user {}", threadId, user.getId());
    }

    @Transactional(rollbackFor = JsonProcessingException.class)
    public ThreadResponse refreshThread(User user, UUID threadId) throws JsonProcessingException {
        ChangeThread thread = threadRepository
                .findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));

        String decryptedPat = gitHubConnectionService.getDecryptedPatForUser(user);
        String[] parts = thread.getRepositoryFullName().split("/");
        String owner = parts[0];
        String repo = parts[1];

        // Fetch diff from GitHub — exception propagates if it fails
        GitHubCompareResponse compare = gitHubApiClient.compare(
                decryptedPat, owner, repo, thread.getBaseBranch(), thread.getHeadBranch()
        );
        if (compare != null) {
            thread.setCommits(serializeToJson(compare.getCommits()));
            thread.setChangedFiles(serializeToJson(compare.getFiles()));
            if (compare.getCommits() != null && !compare.getCommits().isEmpty()) {
                thread.setCommitSha(compare.getCommits()
                        .get(compare.getCommits().size() - 1).getSha());
            }
        }

        thread = threadRepository.save(thread);

        // Create refresh snapshot and timeline event
        snapshotService.createSnapshot(thread, user, com.devbraid.changethread.entity.SnapshotType.REFRESH, null);
        eventService.recordEvent(thread, user, com.devbraid.changethread.entity.ThreadEventType.THREAD_REFRESHED,
                String.format("Thread refreshed from GitHub — %d commits, %d files",
                        thread.getCommits() != null ? countJsonArray(thread.getCommits()) : 0,
                        thread.getChangedFiles() != null ? countJsonArray(thread.getChangedFiles()) : 0),
                null);

        return toResponse(thread);
    }

    /**
     * Run deterministic risk analysis on a thread.
     */
    @Transactional(rollbackFor = Exception.class)
    public ThreadResponse analyzeThread(User user, UUID threadId) throws Exception {
        ChangeThread thread = threadRepository
                .findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));

        var report = riskAnalysisService.analyze(thread.getCommits(), thread.getChangedFiles());
        RiskLevel overallRisk = (RiskLevel) report.get("overallRisk");

        // Transition from DRAFT to ANALYZING
        if (thread.getStatus() == com.devbraid.changethread.entity.ThreadStatus.DRAFT) {
            thread.setStatus(com.devbraid.changethread.entity.ThreadStatus.ANALYZING);
        }

        // Serialization failure propagates — GlobalExceptionHandler handles it
        String riskReport = objectMapper.writeValueAsString(report);

        thread.setRiskLevel(overallRisk);
        thread.setRiskReport(riskReport);
        thread = threadRepository.save(thread);

        // Create analysis snapshot and timeline event
        snapshotService.createSnapshot(thread, user, com.devbraid.changethread.entity.SnapshotType.ANALYSIS,
                String.format("Risk level: %s", overallRisk));
        eventService.recordEvent(thread, user, com.devbraid.changethread.entity.ThreadEventType.ANALYSIS_RUN,
                String.format("Risk analysis complete — level: %s, flags: %d",
                        overallRisk, report.get("flags") != null ? ((java.util.List<?>) report.get("flags")).size() : 0),
                riskReport);

        log.info("Analyzed thread {} — risk level: {}", threadId, overallRisk);
        return toResponse(thread);
    }

    // ── Public helper for ThreadSearchService ────────────────────────

    /**
     * Convert a page of threads to responses with batch-loaded notes — eliminates N+1.
     * Fetches all notes for all threads in a single query.
     */
    public Page<ThreadResponse> toResponsePage(Page<ChangeThread> threadPage) {
        List<ChangeThread> threads = threadPage.getContent();
        if (threads.isEmpty()) {
            return Page.empty();
        }

        Map<UUID, List<NoteResponse>> notesByThreadId = batchLoadNoteResponsesByThreadId(
                threads.stream().map(ChangeThread::getId).toList()
        );

        return threadPage.map(thread -> {
            ThreadResponse response = generalModelMapper.map(thread, ThreadResponse.class);
            response.setNotes(notesByThreadId.getOrDefault(thread.getId(), List.of()));
            return response;
        });
    }

    // ── Private helpers ──────────────────────────────────────────────

    /**
     * Single-thread conversion — acceptable for individual thread pages (1 query).
     */
    private ThreadResponse toResponse(ChangeThread thread) {
        ThreadResponse response = generalModelMapper.map(thread, ThreadResponse.class);

        List<NoteResponse> notes = noteRepository.findByThreadIdOrderByCreatedAtDesc(thread.getId())
                .stream()
                .map(this::toNoteResponse)
                .toList();
        response.setNotes(notes);

        return response;
    }

    /**
     * Batch-load note responses for a list of thread IDs — 1 query instead of N.
     */
    private Map<UUID, List<NoteResponse>> batchLoadNoteResponsesByThreadId(List<UUID> threadIds) {
        if (threadIds.isEmpty()) {
            return Map.of();
        }
        return noteRepository.findByThreadIdInOrderByCreatedAtDesc(threadIds)
                .stream()
                .map(this::toNoteResponse)
                .collect(Collectors.groupingBy(NoteResponse::getThreadId));
    }

    private NoteResponse toNoteResponse(DecisionNote note) {
        NoteResponse response = generalModelMapper.map(note, NoteResponse.class);
        // Nested entity IDs need explicit mapping
        response.setThreadId(note.getThread().getId());
        response.setAuthorId(note.getAuthor().getId());
        return response;
    }

    private String serializeToJson(Object obj) throws JsonProcessingException {
        if (obj == null) return null;
        return objectMapper.writeValueAsString(obj);
    }

    private int countJsonArray(String json) {
        try {
            var list = objectMapper.readValue(json, java.util.List.class);
            return list != null ? list.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
