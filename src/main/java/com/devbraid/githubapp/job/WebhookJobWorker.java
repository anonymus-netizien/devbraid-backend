package com.devbraid.githubapp.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled poller that claims due webhook jobs and hands them to
 * {@link WebhookJobProcessor} one at a time (each in its own transaction).
 * A failed claim must not abort the rest of the poll — hence the boundary catch.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookJobWorker {

    private final WebhookJobProcessor jobProcessor;

    @Value("${app.webhook-jobs.batch-size:5}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.webhook-jobs.poll-interval-ms:2000}")
    public void processDueJobs() {
        for (int i = 0; i < batchSize; i++) {
            try {
                if (!jobProcessor.processOne()) {
                    return;
                }
            } catch (Exception e) {
                // Catch-and-translate at the scheduler boundary — one failed
                // transaction must not abort the rest of the poll.
                log.warn("Webhook job claim/processing failed: {}", e.getMessage());
            }
        }
    }
}
