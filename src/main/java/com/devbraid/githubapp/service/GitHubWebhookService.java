package com.devbraid.githubapp.service;

import com.devbraid.changethread.dto.request.CreateThreadRequest;
import com.devbraid.changethread.service.ChangeThreadService;
import com.devbraid.githubapp.dto.response.WebhookResponse;
import com.devbraid.githubapp.entity.GitHubAppInstallation;
import com.devbraid.githubapp.entity.GitHubWebhook;
import com.devbraid.githubapp.exception.WebhookSignatureInvalidException;
import com.devbraid.githubapp.repository.GitHubAppInstallationRepository;
import com.devbraid.githubapp.repository.GitHubWebhookRepository;
import com.devbraid.review.service.PrReviewTriggerService;
import com.devbraid.user.entity.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;

/**
 * Service for processing GitHub App webhook events.
 * Handles signature verification, event dispatching, and auto-thread creation.
 * <p>
 * All exceptions propagate to GlobalExceptionHandler — no try-catches here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubWebhookService {

    private final GitHubWebhookRepository webhookRepository;
    private final GitHubAppInstallationRepository installationRepository;
    private final ChangeThreadService changeThreadService;
    private final PrReviewTriggerService prReviewTriggerService;
    private final ObjectMapper objectMapper;

    @Value("${github.app.webhook-secret:}")
    private String webhookSecret;

    /**
     * Verify the HMAC-SHA256 signature of a GitHub webhook payload.
     *
     * @param payload   raw request body
     * @param signature X-Hub-Signature-256 header value
     * @return true if signature is valid
     * @throws WebhookSignatureInvalidException if secret is not configured or signature is invalid
     */
    public boolean verifySignature(byte[] payload, String signature) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new WebhookSignatureInvalidException("Webhook secret not configured");
        }

        if (signature == null || !signature.startsWith("sha256=")) {
            return false;
        }

        // ponytail: no try/catch — blank secret already rejected above; HmacSHA256 is
        // guaranteed on every JDK, so createHmac cannot fail at runtime. RuntimeException
        // (if any) propagates to GlobalExceptionHandler per policy.
        Mac mac = com.devbraid.security.SecurityUtils.createHmac(webhookSecret, "HmacSHA256");
        byte[] expectedSignature = mac.doFinal(payload);
        String expectedHex = "sha256=" + HexFormat.of().formatHex(expectedSignature);

        // Constant-time comparison to prevent timing attacks
        return MessageDigest.isEqual(
                expectedHex.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Process a received webhook event.
     * Stores the event and dispatches to appropriate handler.
     *
     * @param eventType      X-GitHub-Event header
     * @param deliveryId     X-GitHub-Delivery header
     * @param action         event action (e.g., "opened", "synchronize")
     * @param payload        parsed JSON payload
     * @param installationId GitHub App installation ID
     * @return webhook response
     * @throws JsonProcessingException if the payload cannot be serialized
     */
    @Transactional
    public WebhookResponse processWebhook(String eventType, String deliveryId,
                                          String action, JsonNode payload,
                                          Long installationId) throws JsonProcessingException {
        // ponytail: no try/catch — checked exceptions propagate to GlobalExceptionHandler.
        // UNIQUE constraint on delivery_id handles dedup; concurrent duplicates get 409 Conflict.
        GitHubWebhook webhook = GitHubWebhook.builder()
                .installationId(installationId)
                .eventType(eventType)
                .action(action)
                .deliveryId(deliveryId)
                .payload(objectMapper.writeValueAsString(payload))
                .processed(false)
                .build();
        webhook = webhookRepository.save(webhook);

        log.info("Received webhook: event={}, action={}, delivery={}", eventType, action, deliveryId);

        dispatchEvent(eventType, action, payload, installationId);
        webhook.setProcessed(true);
        webhook.setProcessedAt(OffsetDateTime.now());
        webhookRepository.save(webhook);

        return toResponse(webhook);
    }

    /**
     * Dispatch webhook event to appropriate handler.
     */
    private void dispatchEvent(String eventType, String action, JsonNode payload, Long installationId) {
        switch (eventType) {
            case "pull_request" -> handlePullRequest(action, payload, installationId);
            case "push" -> handlePush(payload, installationId);
            case "pull_request_review" -> handlePullRequestReview(action, payload, installationId);
            case "installation" -> handleInstallation(action, payload);
            case "ping" -> log.info("GitHub App ping received — app is active");
            default -> log.debug("Unhandled webhook event type: {}", eventType);
        }
    }

    /**
     * Handle pull_request events — auto-create ChangeThread on PR opened/synchronized.
     */
    private void handlePullRequest(String action, JsonNode payload, Long installationId) {
        if (!"opened".equals(action) && !"synchronize".equals(action)) {
            log.debug("Ignoring pull_request action: {}", action);
            return;
        }

        JsonNode prNode = payload.get("pull_request");
        if (prNode == null) return;

        String repoFullName = extractRepoFullName(payload);
        String headBranch = prNode.path("head").path("ref").asText(null);
        String baseBranch = prNode.path("base").path("ref").asText(null);
        String title = prNode.path("title").asText("PR #" + prNode.path("number").asInt());
        String description = prNode.path("body").asText(null);
        int prNumber = prNode.path("number").asInt();
        String headSha = prNode.path("head").path("sha").asText(null);

        if (repoFullName == null || headBranch == null) {
            log.warn("Missing required fields in pull_request webhook");
            return;
        }

        User user = findUserForInstallation(installationId);
        if (user == null) {
            log.warn("No user found for installation {}", installationId);
            return;
        }

        CreateThreadRequest request = new CreateThreadRequest(
                repoFullName,
                headBranch,
                baseBranch,
                title,
                description != null ? description : ""
        );

        // ponytail: no try/catch — thread creation failures propagate to GlobalExceptionHandler
        var threadResponse = changeThreadService.createThread(user, request);
        log.info("Auto-created thread {} for PR #{} on {}", threadResponse.getId(), prNumber, repoFullName);

        // Auto-review the PR (Code-Rabbit-style) — async so the webhook response
        // is never blocked by diff fetching or LLM calls.
        if (headSha != null) {
            prReviewTriggerService.triggerWebhookReview(user, threadResponse.getId(), prNumber, headSha, installationId);
        }
    }

    /**
     * Handle push events — log the push for audit purposes.
     * Future: auto-trigger analysis on push to watched branches.
     */
    private void handlePush(JsonNode payload, Long installationId) {
        String repoFullName = extractRepoFullName(payload);
        String ref = payload.path("ref").asText(null);
        int commitCount = payload.path("commits").size();

        if (repoFullName == null) {
            log.warn("Push event missing repository info — skipping");
            return;
        }

        log.info("Push event on {} ref={} — {} commits", repoFullName, ref, commitCount);
    }

    /**
     * Handle pull_request_review events — log review submissions.
     */
    private void handlePullRequestReview(String action, JsonNode payload, Long installationId) {
        if (!"submitted".equals(action)) return;

        JsonNode reviewNode = payload.get("review");
        if (reviewNode == null) return;

        String state = reviewNode.path("state").asText();
        String repoFullName = extractRepoFullName(payload);
        int prNumber = payload.path("pull_request").path("number").asInt();

        log.info("PR review {} on {} PR #{}", state, repoFullName, prNumber);
    }

    /**
     * Handle installation events (created, deleted, suspend, unsuspend).
     */
    private void handleInstallation(String action, JsonNode payload) {
        Long installationId = payload.path("installation").path("id").asLong();

        switch (action) {
            case "created" -> log.info("GitHub App installed — installation {}", installationId);
            case "deleted" -> log.info("GitHub App uninstalled — installation {}", installationId);
            case "suspend" -> log.info("GitHub App suspended — installation {}", installationId);
            case "unsuspend" -> log.info("GitHub App unsuspended — installation {}", installationId);
            default -> log.debug("Unhandled installation action: {}", action);
        }
    }

    /**
     * Find the user associated with a GitHub App installation.
     */
    private User findUserForInstallation(Long installationId) {
        return installationRepository.findByInstallationId(installationId)
                .map(GitHubAppInstallation::getUser)
                .orElse(null);
    }

    /**
     * Extract repository full name (owner/repo) from webhook payload.
     */
    private String extractRepoFullName(JsonNode payload) {
        JsonNode repoNode = payload.get("repository");
        if (repoNode == null) return null;
        String fullName = repoNode.path("full_name").asText(null);
        if (fullName == null) {
            String owner = repoNode.path("owner").path("login").asText(null);
            String name = repoNode.path("name").asText(null);
            if (owner != null && name != null) {
                fullName = owner + "/" + name;
            }
        }
        return fullName;
    }

    private WebhookResponse toResponse(GitHubWebhook webhook) {
        return WebhookResponse.builder()
                .id(webhook.getId())
                .installationId(webhook.getInstallationId())
                .deliveryId(webhook.getDeliveryId())
                .eventType(webhook.getEventType())
                .action(webhook.getAction())
                .processed(webhook.getProcessed())
                .processingError(webhook.getProcessingError())
                .receivedAt(webhook.getReceivedAt())
                .processedAt(webhook.getProcessedAt())
                .build();
    }
}
