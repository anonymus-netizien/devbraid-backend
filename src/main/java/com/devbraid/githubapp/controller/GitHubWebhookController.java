package com.devbraid.githubapp.controller;

import com.devbraid.common.ApiResponse;
import com.devbraid.githubapp.dto.response.WebhookResponse;
import com.devbraid.githubapp.service.GitHubWebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.stream.Collectors;

/**
 * Controller for receiving GitHub App webhook events.
 * All requests are unauthenticated — GitHub signature verification is the auth mechanism.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class GitHubWebhookController {

    private final GitHubWebhookService webhookService;
    private final ObjectMapper objectMapper;

    /**
     * Receive GitHub webhook events.
     * GitHub sends POST requests with X-GitHub-Event, X-GitHub-Delivery, and X-Hub-Signature-256 headers.
     */
    @PostMapping("/github")
    public ResponseEntity<ApiResponse<WebhookResponse>> handleGitHubWebhook(
            HttpServletRequest request,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {

        try {
            // Read raw body with size limit (GitHub max is 25MB)
            long contentLength = request.getContentLengthLong();
            if (contentLength > 25 * 1024 * 1024) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("Payload too large"));
            }
            byte[] rawBody = request.getInputStream().readAllBytes();
            if (rawBody.length > 25 * 1024 * 1024) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("Payload too large"));
            }

            // Verify HMAC-SHA256 signature
            if (!webhookService.verifySignature(rawBody, signature)) {
                log.warn("Invalid webhook signature — delivery={}", deliveryId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Invalid signature"));
            }

            // Parse JSON payload
            JsonNode payload = objectMapper.readTree(rawBody);

            // Extract action from payload (null for push, ping events)
            String action = payload.has("action") ? payload.path("action").asText() : null;

            // Extract installation ID
            Long installationId = null;
            if (payload.has("installation")) {
                installationId = payload.path("installation").path("id").asLong();
            }

            // Process the webhook
            WebhookResponse response = webhookService.processWebhook(
                    eventType, deliveryId, action, payload, installationId);

            return ResponseEntity.ok(ApiResponse.success("Webhook processed", response));

        } catch (IOException e) {
            log.error("Failed to read webhook body", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Invalid request body"));
        } catch (Exception e) {
            log.error("Failed to process webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal error processing webhook"));
        }
    }

    /**
     * Health check endpoint for webhook configuration verification.
     */
    @GetMapping("/github/health")
    public ResponseEntity<ApiResponse<String>> webhookHealth() {
        return ResponseEntity.ok(ApiResponse.success("Webhook endpoint is active", "ok"));
    }
}
