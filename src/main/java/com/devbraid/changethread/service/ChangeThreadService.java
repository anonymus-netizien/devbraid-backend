package com.devbraid.changethread.service;

import com.devbraid.analysis.RiskLevel;
import com.devbraid.analysis.service.RiskAnalysisService;
import com.devbraid.changethread.dto.request.CreateThreadRequest;
import com.devbraid.changethread.dto.request.UpdateThreadRequest;
import com.devbraid.changethread.dto.response.NoteResponse;
import com.devbraid.changethread.dto.response.ThreadResponse;
import com.devbraid.changethread.entity.ChangeThread;
import com.devbraid.changethread.entity.DecisionNote;
import com.devbraid.changethread.exception.ThreadNotFoundException;
import com.devbraid.changethread.repository.ChangeThreadRepository;
import com.devbraid.changethread.repository.DecisionNoteRepository;
import com.devbraid.github.client.GitHubApiClient;
import com.devbraid.github.dto.response.ChangedFileDto;
import com.devbraid.github.dto.response.CommitSummaryDto;
import com.devbraid.github.dto.response.GitHubCompareResponse;
import com.devbraid.github.service.GitHubConnectionService;
import com.devbraid.user.entity.User;
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
    private final RiskAnalysisService riskAnalysisService;
    private final ModelMapper generalModelMapper;

    /**
     * Create a new Change Thread by fetching diff data from GitHub using the user's connected PAT.
     */
    @Transactional
    public ThreadResponse createThread(User user, CreateThreadRequest req) {
        String decryptedPat = gitHubConnectionService.getDecryptedPatForUser(user);
        return createThreadInternal(user, decryptedPat, req);
    }

    private ThreadResponse createThreadInternal(User user, String accessToken, CreateThreadRequest req) {
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

        ThreadData data = fetchThreadData(accessToken, owner, repo, baseBranch, req.getHeadBranch());

        ChangeThread thread = ChangeThread.builder()
                .user(user)
                .repositoryFullName(req.getRepositoryFullName())
                .headBranch(req.getHeadBranch())
                .baseBranch(baseBranch)
                .title(req.getTitle())
                .description(req.getDescription())
                .commitSha(data.latestCommitSha())
                .commits(data.commits())
                .changedFiles(data.changedFiles())
                .build();

        thread = threadRepository.save(thread);
        log.info("Created thread {} for user {} on {}/{}", thread.getId(), user.getId(), owner, repo);

        return toResponse(thread);
    }

    /**
     * Fetch the commit/diff data for a thread from GitHub.
     */
    private ThreadData fetchThreadData(String accessToken, String owner, String repo, String baseBranch, String headBranch) {
        List<CommitSummaryDto> commits = null;
        List<ChangedFileDto> changedFiles = null;
        String latestCommitSha = null;

        if (headBranch.equalsIgnoreCase(baseBranch)) {
            // Single-Branch Mode: fetch recent commits directly for this branch
            List<CommitSummaryDto> branchCommits =
                    gitHubApiClient.listCommits(accessToken, owner, repo, headBranch, 20);
            if (branchCommits != null && !branchCommits.isEmpty()) {
                commits = branchCommits;
                latestCommitSha = branchCommits.get(0).getSha();
            }
        } else {
            // Cross-Branch Compare Mode
            GitHubCompareResponse compare = gitHubApiClient.compare(
                    accessToken, owner, repo, baseBranch, headBranch
            );
            if (compare != null) {
                commits = compare.getCommits();
                changedFiles = compare.getFiles();
                if (compare.getCommits() != null && !compare.getCommits().isEmpty()) {
                    latestCommitSha = compare.getCommits()
                            .get(compare.getCommits().size() - 1).getSha();
                }
            }
        }
        return new ThreadData(commits, changedFiles, latestCommitSha);
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

    @Transactional
    public ThreadResponse refreshThread(User user, UUID threadId) {
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
            thread.setCommits(compare.getCommits());
            thread.setChangedFiles(compare.getFiles());
            if (compare.getCommits() != null && !compare.getCommits().isEmpty()) {
                thread.setCommitSha(compare.getCommits()
                        .get(compare.getCommits().size() - 1).getSha());
            }
        }

        thread = threadRepository.save(thread);

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

        if (thread.getStatus() == com.devbraid.changethread.entity.ThreadStatus.DRAFT) {
            thread.setStatus(com.devbraid.changethread.entity.ThreadStatus.ANALYZING);
        }

        thread.setRiskLevel(overallRisk);
        thread.setRiskReport(report);
        thread = threadRepository.save(thread);

        log.info("Analyzed thread {} — risk level: {}", threadId, overallRisk);
        return toResponse(thread);
    }

    // ── Public helper ────────────────────────────────────────────────

    /**
     * Convert a page of threads to responses with batch-loaded notes — eliminates N+1.
     * Fetches all notes for all threads in a single query.
     */
    public Page<ThreadResponse> toResponsePage(Page<ChangeThread> threadPage) {
        List<ChangeThread> threads = threadPage.getContent();
        if (threads.isEmpty()) {
            return Page.empty();
        }

        Map<UUID, List<NoteResponse>> notesByThreadId = batchLoadNotesByThreadIds(
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
    private Map<UUID, List<NoteResponse>> batchLoadNotesByThreadIds(List<UUID> threadIds) {
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

    private record ThreadData(List<CommitSummaryDto> commits, List<ChangedFileDto> changedFiles,
                              String latestCommitSha) {
    }

}