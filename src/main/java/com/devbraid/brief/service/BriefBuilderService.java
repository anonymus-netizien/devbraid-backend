package com.devbraid.brief.service;

import com.devbraid.ai.service.AIProvider;
import com.devbraid.brief.dto.BriefListItemResponse;
import com.devbraid.brief.dto.BriefResponse;
import com.devbraid.brief.entity.ChangeBrief;
import com.devbraid.brief.repository.ChangeBriefRepository;
import com.devbraid.changethread.entity.ChangeThread;
import com.devbraid.changethread.entity.ThreadStatus;
import com.devbraid.changethread.exception.BriefNotFoundException;
import com.devbraid.changethread.exception.ThreadNotFoundException;
import com.devbraid.changethread.repository.ChangeThreadRepository;
import com.devbraid.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BriefBuilderService {

    private final ChangeBriefRepository briefRepository;
    private final ChangeThreadRepository threadRepository;
    private final AIProvider aiProvider;

    /**
     * Generate a Change Brief for a thread using AI.
     *
     * @param user     the authenticated user
     * @param threadId the thread to generate a brief for
     * @return the generated brief response
     */
    @Transactional
    public BriefResponse generateBrief(User user, UUID threadId) {
        ChangeThread thread = threadRepository
                .findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));

        String content;
        try {
            content = aiProvider.analyze(buildBriefPrompt(thread));
        } catch (Exception e) {
            log.warn("AI brief generation unavailable, using template fallback: {}", e.getMessage());
            content = buildTemplateBrief(thread);
        }

        // Check if brief already exists
        var existingBrief = briefRepository.findByThreadId(threadId);
        ChangeBrief brief;

        if (existingBrief.isPresent()) {
            brief = existingBrief.get();
            brief.setContent(content);
        } else {
            brief = ChangeBrief.builder()
                    .thread(thread)
                    .content(content)
                    .build();
        }

        brief = briefRepository.save(brief);

        // Update thread status to READY if it was DRAFT or ANALYZING
        if (thread.getStatus() == ThreadStatus.DRAFT || thread.getStatus() == ThreadStatus.ANALYZING) {
            thread.setStatus(ThreadStatus.READY);
            threadRepository.save(thread);
        }

        log.info("Generated brief {} for thread {}", brief.getId(), threadId);
        return toResponse(brief);
    }

    /**
     * Get paginated list of all briefs for the current user.
     */
    @Transactional(readOnly = true)
    public Page<BriefListItemResponse> listBriefsByUser(User user, Pageable pageable) {
        return briefRepository.findAllByUserId(user.getId(), pageable)
                .map(this::toListItemResponse);
    }

    /**
     * Get a brief by its ID with ownership verification.
     */
    @Transactional(readOnly = true)
    public BriefResponse getBriefById(User user, UUID briefId) {
        ChangeBrief brief = briefRepository.findById(briefId)
                .orElseThrow(() -> new BriefNotFoundException("Brief not found"));
        if (!brief.getThread().getUser().getId().equals(user.getId())) {
            throw new BriefNotFoundException("Brief not found");
        }
        return toResponse(brief);
    }

    /**
     * Get existing brief for a thread.
     */
    @Transactional(readOnly = true)
    public BriefResponse getBrief(User user, UUID threadId) {
        threadRepository.findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));

        return briefRepository.findByThreadId(threadId)
                .map(this::toResponse)
                .orElse(null);
    }

    private String buildBriefPrompt(ChangeThread thread) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate a structured Change Brief for this code change.\n\n");
        prompt.append("## Thread: ").append(thread.getTitle()).append("\n");
        prompt.append("Repository: ").append(thread.getRepositoryFullName()).append("\n");
        prompt.append("Branch: ").append(thread.getHeadBranch()).append(" → ").append(thread.getBaseBranch()).append("\n\n");

        if (thread.getCommits() != null) {
            prompt.append("### Commits\n").append(thread.getCommits()).append("\n\n");
        }
        if (thread.getChangedFiles() != null) {
            prompt.append("### Changed Files\n").append(thread.getChangedFiles()).append("\n\n");
        }
        if (thread.getRiskReport() != null) {
            prompt.append("### Risk Assessment\n").append(thread.getRiskReport()).append("\n\n");
        }

        prompt.append("Generate a Markdown brief with:\n");
        prompt.append("1. **Summary** — What changed and why\n");
        prompt.append("2. **Key Changes** — List of important modifications\n");
        prompt.append("3. **Risk Assessment** — Potential risks and mitigations\n");
        prompt.append("4. **Testing Recommendations** — What to test\n");

        return prompt.toString();
    }

    private String buildTemplateBrief(ChangeThread thread) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Change Brief: ").append(thread.getTitle()).append("\n\n");
        sb.append("**Repository:** ").append(thread.getRepositoryFullName()).append("\n");
        sb.append("**Branch:** ").append(thread.getHeadBranch()).append(" → ").append(thread.getBaseBranch()).append("\n\n");
        sb.append("## Summary\n\n");
        sb.append("This change encompasses modifications across ").append(thread.getRepositoryFullName()).append(" ");
        sb.append("from branch ").append(thread.getHeadBranch()).append(" into ").append(thread.getBaseBranch()).append(".\n\n");

        if (thread.getRiskLevel() != null) {
            sb.append("## Risk Assessment\n\n");
            sb.append("**Overall Risk:** ").append(thread.getRiskLevel()).append("\n\n");
        }

        if (thread.getRiskReport() != null) {
            sb.append("### Risk Flags\n\n");
            sb.append(thread.getRiskReport()).append("\n\n");
        }

        sb.append("---\n\n");
        sb.append("*This brief was generated using template fallback (AI service unavailable).\n");
        sb.append("Configure OPENAI_API_KEY for AI-enhanced briefs.*\n");

        return sb.toString();
    }

    private BriefResponse toResponse(ChangeBrief brief) {
        return BriefResponse.builder()
                .id(brief.getId())
                .threadId(brief.getThread().getId())
                .content(brief.getContent())
                .publishedToGithub(brief.getPublishedAt() != null)
                .publishUrl(brief.getPublishUrl())
                .createdAt(brief.getCreatedAt())
                .build();
    }

    private BriefListItemResponse toListItemResponse(ChangeBrief brief) {
        ChangeThread thread = brief.getThread();
        return BriefListItemResponse.builder()
                .id(brief.getId())
                .threadId(thread.getId())
                .threadTitle(thread.getTitle())
                .repositoryFullName(thread.getRepositoryFullName())
                .headBranch(thread.getHeadBranch())
                .baseBranch(thread.getBaseBranch())
                .threadStatus(thread.getStatus())
                .publishedToGithub(brief.getPublishedAt() != null)
                .createdAt(brief.getCreatedAt())
                .build();
    }
}
