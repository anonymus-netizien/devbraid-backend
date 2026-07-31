package com.devbraid.changethread.event;

import com.devbraid.changethread.entity.ThreadStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Publishes thread status transitions to /topic/threads/{id} for live UI updates.
 * ponytail: fire-and-forget broadcast — the REST response remains the source of truth.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThreadStatusEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishStatus(UUID threadId, ThreadStatus status) {
        try {
            // ponytail: simple record payload avoids SimpMessagingTemplate.convertAndSend overload ambiguity
            messagingTemplate.convertAndSend(
                    "/topic/threads/" + threadId,
                    new StatusEvent("THREAD_STATUS", threadId, status.name())
            );
        } catch (Exception e) {
            // ponytail: a failed broadcast must never break the REST call that caused it
            log.warn("Failed to publish status for thread {}: {}", threadId, e.getMessage());
        }
    }

    private record StatusEvent(String type, UUID threadId, String status) {}
}
