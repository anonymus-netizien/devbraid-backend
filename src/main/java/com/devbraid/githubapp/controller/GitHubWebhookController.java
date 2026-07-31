package com.devbraid.githubapp.controller;

import com.devbraid.common.ApiResponse;
import com.devbraid.githubapp.dto.response.WebhookResponse;
import com.devbraid.githubapp.exception.WebhookPayloadTooLargeException;
import com.devbraid.githubapp.exception.WebhookSignatureInvalidException;
import com.devbraid.githubapp.service.GitHubWebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * Controller for receiving GitHub App webhook events.
 * All requests are unauthenticated — GitHub signature verification is the auth mechanism.
 * <p>
 * All exceptions propagate to GlobalExceptionHandler — no try-catches here.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class GitHubWebhookController {

    private static final long MAX_PAYLOAD_SIZE = 25 * 1024 * 1024; // 25MB — GitHub's max

    private final GitHubWebhookService webhookService;
    private final ObjectMapper objectMapper;

    /**
     * Receive GitHub webhook events.
     * GitHub sends POST requests with X-GitHub-Event, X-GitHub-Delivery, and X-Hub-Signature-256 headers.
     *
     * @throws WebhookPayloadTooLargeException  if payload exceeds 25MB
     * @throws WebhookSignatureInvalidException if signature verification fails
     * @throws WebhookPayloadInvalidException   if request body cannot be read or parsed
     */
    @PostMapping("/github")
    public ResponseEntity<ApiResponse<WebhookResponse>> handleGitHubWebhook(
            HttpServletRequest request,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) throws IOException {

        // Read raw body with size limit
        long contentLength = request.getContentLengthLong();
        if (contentLength > MAX_PAYLOAD_SIZE) {
            throw new WebhookPayloadTooLargeException("Payload too large: " + contentLength + " bytes");
        }

        byte[] rawBody = request.getInputStream().readAllBytes();
        if (rawBody.length > MAX_PAYLOAD_SIZE) {
            throw new WebhookPayloadTooLargeException("Payload too large: " + rawBody.length + " bytes");
        }

        // Verify HMAC-SHA256 signature — throws WebhookSignatureInvalidException if invalid
        if (!webhookService.verifySignature(rawBody, signature)) {
            throw new WebhookSignatureInvalidException("Invalid webhook signature");
        }

        // Parse JSON payload — JsonProcessingException propagates to GlobalExceptionHandler
        JsonNode payload = objectMapper.readTree(rawBody);

        // Extract action from payload (null for push, ping events)
        String action = payload.has("action") ? payload.path("action").asText() : null;

        // Extract installation ID
        Long installationId = null;
        if (payload.has("installation")) {
            installationId = payload.path("installation").path("id").asLong();
        }

        // Process the webhook — exceptions propagate to GlobalExceptionHandler
        WebhookResponse response = webhookService.processWebhook(
                eventType, deliveryId, action, payload, installationId);

        return ResponseEntity.ok(ApiResponse.success("Webhook processed", response));
    }

    /**
     * Health check endpoint for webhook configuration verification.
     */
    @GetMapping("/github/health")
    public ResponseEntity<ApiResponse<String>> webhookHealth() {
        return ResponseEntity.ok(ApiResponse.success("Webhook endpoint is active", "ok"));
    }
}
