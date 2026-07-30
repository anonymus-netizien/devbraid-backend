package com.devbraid.brief.service;

import com.devbraid.ai.service.AIProvider;
import com.devbraid.ai.service.PromptBuilder;
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
import org.modelmapper.ModelMapper;
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
    private final PromptBuilder promptBuilder;
    private final ModelMapper generalModelMapper;

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
            content = aiProvider.analyze(promptBuilder.buildBriefPrompt(thread));
        } catch (Exception e) {
            log.warn("AI brief generation unavailable, using template fallback: {}", e.getMessage());
            content = promptBuilder.buildTemplateBrief(thread);
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

    private BriefResponse toResponse(ChangeBrief brief) {
        BriefResponse response = generalModelMapper.map(brief, BriefResponse.class);
        response.setThreadId(brief.getThread().getId());
        response.setPublishedToGithub(brief.getPublishedAt() != null);
        return response;
    }

    private BriefListItemResponse toListItemResponse(ChangeBrief brief) {
        ChangeThread thread = brief.getThread();
        BriefListItemResponse response = generalModelMapper.map(brief, BriefListItemResponse.class);
        response.setThreadId(thread.getId());
        response.setThreadTitle(thread.getTitle());
        response.setRepositoryFullName(thread.getRepositoryFullName());
        response.setHeadBranch(thread.getHeadBranch());
        response.setBaseBranch(thread.getBaseBranch());
        response.setThreadStatus(thread.getStatus());
        response.setPublishedToGithub(brief.getPublishedAt() != null);
        return response;
    }
}
