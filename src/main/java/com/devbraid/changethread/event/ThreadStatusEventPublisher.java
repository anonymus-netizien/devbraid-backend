package com.devbraid.changethread.event;

import com.devbraid.changethread.entity.ThreadStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

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

    // ponytail: fire-and-forget — @Async runs this off the request thread, so a WebSocket
    // broadcast failure can never fail the REST call that triggered it (no try/catch needed).
    // Simple record payload avoids SimpMessagingTemplate.convertAndSend overload ambiguity.
    @Async
    public void publishStatus(UUID threadId, ThreadStatus status) {
        messagingTemplate.convertAndSend(
                "/topic/threads/" + threadId,
                new StatusEvent("THREAD_STATUS", threadId, status.name())
        );
    }

    private record StatusEvent(String type, UUID threadId, String status) {
    }
}
