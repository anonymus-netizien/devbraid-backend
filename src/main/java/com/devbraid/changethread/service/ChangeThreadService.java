package com.devbraid.changethread.service;

import com.devbraid.analysis.service.RiskAnalysisService;
import com.devbraid.changethread.dto.request.CreateThreadRequest;
import com.devbraid.changethread.dto.request.UpdateThreadRequest;
import com.devbraid.changethread.dto.response.NoteResponse;
import com.devbraid.changethread.dto.response.ThreadResponse;
import com.devbraid.changethread.entity.ChangeThread;
import com.devbraid.changethread.entity.DecisionNote;
import com.devbraid.changethread.entity.RiskLevel;
import com.devbraid.changethread.exception.ThreadNotFoundException;
import com.devbraid.changethread.repository.ChangeThreadRepository;
import com.devbraid.changethread.repository.DecisionNoteRepository;
import com.devbraid.github.client.GitHubApiClient;
import com.devbraid.github.dto.response.GitHubCompareResponse;
import com.devbraid.github.entity.GitHubConnection;
import com.devbraid.github.exception.GitHubNotConnectedException;
import com.devbraid.github.repository.GitHubConnectionRepository;
import com.devbraid.github.util.PatEncryptor;
import com.devbraid.user.entity.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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
    private final GitHubConnectionRepository connectionRepository;
    private final GitHubApiClient gitHubApiClient;
    private final PatEncryptor patEncryptor;
    private final ObjectMapper objectMapper;
    private final RiskAnalysisService riskAnalysisService;

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
        GitHubConnection connection = connectionRepository
                .findByUserId(user.getId())
                .orElseThrow(() -> new GitHubNotConnectedException("Connect GitHub first"));

        String decryptedPat = decryptPat(connection);

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
        return toResponse(thread);
    }

    @Transactional(readOnly = true)
    public Page<ThreadResponse> listThreads(User user, Pageable pageable) {
        return threadRepository
                .findAllByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(this::toResponse);
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

    @Transactional(rollbackFor = JsonProcessingException.class)
    public ThreadResponse refreshThread(User user, UUID threadId) throws JsonProcessingException {
        ChangeThread thread = threadRepository
                .findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));

        GitHubConnection connection = connectionRepository
                .findByUserId(user.getId())
                .orElseThrow(() -> new GitHubNotConnectedException("Connect GitHub first"));

        String decryptedPat = decryptPat(connection);
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

        // Serialization failure propagates — GlobalExceptionHandler handles it
        String riskReport = objectMapper.writeValueAsString(report);

        thread.setRiskLevel(overallRisk);
        thread.setRiskReport(riskReport);
        thread = threadRepository.save(thread);

        log.info("Analyzed thread {} — risk level: {}", threadId, overallRisk);
        return toResponse(thread);
    }

    // ── Private helpers ──────────────────────────────────────────────

    private ThreadResponse toResponse(ChangeThread thread) {
        var notes = noteRepository.findByThreadIdOrderByCreatedAtDesc(thread.getId())
                .stream()
                .map(this::toNoteResponse)
                .toList();

        return ThreadResponse.builder()
                .id(thread.getId())
                .repositoryFullName(thread.getRepositoryFullName())
                .headBranch(thread.getHeadBranch())
                .baseBranch(thread.getBaseBranch())
                .title(thread.getTitle())
                .description(thread.getDescription())
                .source(thread.getSource())
                .status(thread.getStatus())
                .commitSha(thread.getCommitSha())
                .commits(thread.getCommits())
                .changedFiles(thread.getChangedFiles())
                .riskLevel(thread.getRiskLevel())
                .riskReport(thread.getRiskReport())
                .notes(notes)
                .createdAt(thread.getCreatedAt())
                .updatedAt(thread.getUpdatedAt())
                .build();
    }

    private NoteResponse toNoteResponse(DecisionNote note) {
        return NoteResponse.builder()
                .id(note.getId())
                .threadId(note.getThread().getId())
                .authorId(note.getAuthor().getId())
                .context(note.getContext())
                .contextRef(note.getContextRef())
                .decision(note.getDecision())
                .rationale(note.getRationale())
                .alternatives(note.getAlternatives())
                .impact(note.getImpact())
                .status(note.getStatus())
                .createdAt(note.getCreatedAt())
                .build();
    }

    private String serializeToJson(Object obj) throws JsonProcessingException {
        if (obj == null) return null;
        return objectMapper.writeValueAsString(obj);
    }

    private String decryptPat(GitHubConnection connection) {
        // ponytail: RuntimeException from PatEncryptor propagates directly
        return patEncryptor.decrypt(connection.getEncryptedPat(), connection.getIv());
    }
}
