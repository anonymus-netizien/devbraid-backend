package com.devbraid.changethread.service;

import com.devbraid.changethread.dto.request.CreateThreadEventRequest;
import com.devbraid.changethread.dto.response.ThreadEventResponse;
import com.devbraid.changethread.entity.ChangeThread;
import com.devbraid.changethread.entity.ThreadEvent;
import com.devbraid.changethread.entity.ThreadEventType;
import com.devbraid.changethread.exception.ThreadNotFoundException;
import com.devbraid.changethread.repository.ChangeThreadRepository;
import com.devbraid.changethread.repository.ThreadEventRepository;
import com.devbraid.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing Thread Events — the timeline of all actions on a thread.
 * Events are immutable once created. No update or delete operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThreadEventService {

    private final ThreadEventRepository eventRepository;
    private final ChangeThreadRepository threadRepository;
    private final ModelMapper generalModelMapper;

    /**
     * Record an event on a thread. Used internally by other services.
     */
    @Transactional
    public ThreadEvent recordEvent(ChangeThread thread, User actor, ThreadEventType type, String summary, String metadata) {
        ThreadEvent event = ThreadEvent.builder()
                .thread(thread)
                .actor(actor)
                .type(type)
                .summary(summary)
                .metadata(metadata)
                .build();

        event = eventRepository.save(event);
        log.info("Recorded event {} for thread {}: {}", type, thread.getId(), summary);
        return event;
    }

    /**
     * Create a manual event added by the user.
     */
    @Transactional
    public ThreadEventResponse createManualEvent(User user, UUID threadId, CreateThreadEventRequest req) {
        ChangeThread thread = threadRepository
                .findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));

        ThreadEvent event = recordEvent(thread, user, ThreadEventType.MANUAL, req.getSummary(), req.getMetadata());
        return toResponse(event);
    }

    /**
     * List all events for a thread, ordered by creation time descending.
     */
    @Transactional(readOnly = true)
    public List<ThreadEventResponse> listEvents(User user, UUID threadId) {
        threadRepository.findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));

        return eventRepository.findByThreadIdOrderByCreatedAtDesc(threadId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * List events for a thread with pagination.
     */
    @Transactional(readOnly = true)
    public Page<ThreadEventResponse> listEventsPaged(User user, UUID threadId, Pageable pageable) {
        threadRepository.findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));

        return eventRepository.findByThreadIdOrderByCreatedAtDesc(threadId, pageable)
                .map(this::toResponse);
    }

    // ── Private helpers ──────────────────────────────────────────────

    private ThreadEventResponse toResponse(ThreadEvent event) {
        ThreadEventResponse response = generalModelMapper.map(event, ThreadEventResponse.class);
        response.setThreadId(event.getThread().getId());
        response.setActorId(event.getActor().getId());
        return response;
    }
}
