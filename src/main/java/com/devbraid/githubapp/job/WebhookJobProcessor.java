package com.devbraid.githubapp.job;

import com.devbraid.githubapp.entity.GitHubWebhook;
import com.devbraid.githubapp.entity.JobStatus;
import com.devbraid.githubapp.entity.WebhookJob;
import com.devbraid.githubapp.exception.WebhookNotFoundException;
import com.devbraid.githubapp.exception.WebhookPayloadInvalidException;
import com.devbraid.githubapp.repository.GitHubWebhookRepository;
import com.devbraid.githubapp.repository.WebhookJobRepository;
import com.devbraid.githubapp.service.GitHubWebhookService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Claims and processes a single due webhook job within its own transaction.
 * Per-job transactions keep one failing job from rolling back the SUCCEEDED
 * state of the rest of the poll batch; SKIP LOCKED prevents concurrent workers
 * (or instances) from claiming the same row.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookJobProcessor {

    private final WebhookJobRepository jobRepository;
    private final GitHubWebhookRepository webhookRepository;
    private final GitHubWebhookService webhookService;
    private final ObjectMapper objectMapper;

    @Value("${app.webhook-jobs.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.webhook-jobs.backoff-base-ms:2000}")
    private long backoffBaseMs;

    /**
     * @return true when a job was claimed and processed, false when the queue is empty
     */
    @Transactional
    public boolean processOne() {
        List<WebhookJob> claimed = jobRepository.claimBatch(1);
        if (claimed.isEmpty()) {
            return false;
        }
        WebhookJob job = claimed.get(0);
        job.setStatus(JobStatus.PROCESSING);

        try {
            dispatch(job);
            job.setStatus(JobStatus.SUCCEEDED);
            log.info("Webhook job {} (webhook {}) succeeded", job.getId(), job.getWebhookId());
        } catch (Exception e) {
            // Catch-and-translate at the scheduler boundary: a job failure must be
            // recorded as backoff/dead-letter state, not rethrown into the poll loop.
            job.setAttempts(job.getAttempts() + 1);
            job.setLastError(truncate(e.getMessage()));
            if (job.getAttempts() >= maxAttempts) {
                job.setStatus(JobStatus.FAILED);
                log.error("Webhook job {} (webhook {}) dead-lettered after {} attempts: {}",
                        job.getId(), job.getWebhookId(), job.getAttempts(), e.getMessage());
            } else {
                job.setStatus(JobStatus.PENDING);
                long delayMillis = backoffBaseMs * (1L << (job.getAttempts() - 1));
                job.setNextAttemptAt(OffsetDateTime.now()
                        .plusNanos(TimeUnit.MILLISECONDS.toNanos(delayMillis)));
                log.warn("Webhook job {} failed (attempt {} of {}), retrying in {}ms: {}",
                        job.getId(), job.getAttempts(), maxAttempts, delayMillis, e.getMessage());
            }
        }
        jobRepository.save(job);
        return true;
    }

    private void dispatch(WebhookJob job) {
        GitHubWebhook webhook = webhookRepository.findById(job.getWebhookId())
                .orElseThrow(() -> new WebhookNotFoundException("Webhook not found: " + job.getWebhookId()));
        JsonNode payload;
        try {
            payload = objectMapper.readTree(job.getPayload());
        } catch (JsonProcessingException e) {
            throw new WebhookPayloadInvalidException("Stored webhook payload is not valid JSON", e);
        }
        webhookService.dispatch(job.getEventType(), webhook.getAction(), payload, webhook.getInstallationId());
        webhook.setProcessed(true);
        webhook.setProcessedAt(OffsetDateTime.now());
        webhookRepository.save(webhook);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
