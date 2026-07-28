package com.devbraid.changethread.service;

import com.devbraid.analysis.dto.RiskFlagDto;
import com.devbraid.analysis.service.EvidenceExtractor;
import com.devbraid.analysis.service.RiskFlagRules;
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
import com.devbraid.github.exception.GitHubTokenInvalidException;
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

import java.util.UUID;

/**
 * Service for managing Change Threads.
 * Handles CRUD operations, GitHub diff fetching, and thread lifecycle.
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
    private final RiskFlagRules riskFlagRules;
    private final EvidenceExtractor evidenceExtractor;

    /**
     * Create a new Change Thread by fetching diff data from GitHub.
     *
     * @param user the authenticated user
     * @param req  thread creation request
     * @return created thread response
     * @throws GitHubNotConnectedException if user has no GitHub connection
     */
    @Transactional
    public ThreadResponse createThread(User user, CreateThreadRequest req) {
        // Validate GitHub connection exists
        GitHubConnection connection = connectionRepository
                .findByUserId(user.getId())
                .orElseThrow(() -> new GitHubNotConnectedException("Connect GitHub first"));

        String decryptedPat = decryptPat(connection);

        // Parse owner/repo from full name
        String[] parts = req.getRepositoryFullName().split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid repository format. Use 'owner/repo'");
        }
        String owner = parts[0];
        String repo = parts[1];

        // Base branch must be provided (no getDefaultBranch in API client)
        String baseBranch = req.getBaseBranch();
        if (baseBranch == null || baseBranch.isBlank()) {
            baseBranch = "main";
        }

        // Fetch diff from GitHub
        String commitsJson = null;
        String changedFilesJson = null;
        String latestCommitSha = null;

        try {
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
        } catch (com.devbraid.github.exception.GitHubTokenInvalidException
                 | com.devbraid.github.exception.GitHubRateLimitException e) {
            throw e; // Re-throw auth/rate-limit errors — user must act
        } catch (Exception e) {
            log.warn("Failed to fetch diff from GitHub: {}", e.getMessage());
        }

        // Build and persist thread
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

    @Transactional
    public ThreadResponse refreshThread(User user, UUID threadId) {
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

        try {
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
        } catch (Exception e) {
            log.warn("Failed to refresh diff from GitHub: {}", e.getMessage());
        }

        thread = threadRepository.save(thread);
        return toResponse(thread);
    }

    /**
     * Run deterministic risk analysis on a thread.
     */
    @Transactional
    public ThreadResponse analyzeThread(User user, UUID threadId) {
        ChangeThread thread = threadRepository
                .findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));

        // Run deterministic rules
        var flags = riskFlagRules.evaluate(thread.getCommits(), thread.getChangedFiles());
        var evidence = evidenceExtractor.extract(thread.getCommits(), thread.getChangedFiles());
        RiskLevel overallRisk = riskFlagRules.calculateOverallRisk(flags);

        // Build risk report as JSON
        String riskReport;
        try {
            var report = new java.util.HashMap<String, Object>();
            report.put("flags", flags);
            report.put("evidence", evidence);
            riskReport = objectMapper.writeValueAsString(report);
        } catch (Exception e) {
            riskReport = null;
        }

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

    private String serializeToJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize to JSON: {}", e.getMessage());
            return null;
        }
    }

    private String decryptPat(GitHubConnection connection) {
        try {
            return patEncryptor.decrypt(connection.getEncryptedPat(), connection.getIv());
        } catch (Exception e) {
            throw new GitHubTokenInvalidException("Failed to decrypt GitHub token");
        }
    }
}
