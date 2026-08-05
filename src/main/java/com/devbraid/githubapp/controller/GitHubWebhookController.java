package com.devbraid.githubapp.controller;

import com.devbraid.common.ApiResponse;
import com.devbraid.common.api.ApiErrorResponses;
import com.devbraid.githubapp.dto.response.WebhookResponse;
import com.devbraid.githubapp.exception.WebhookPayloadTooLargeException;
import com.devbraid.githubapp.exception.WebhookSignatureInvalidException;
import com.devbraid.githubapp.service.GitHubWebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
@Tag(name = "GitHub Webhooks", description = "Receives GitHub App webhook events. Unauthenticated — the HMAC-SHA256 signature in `X-Hub-Signature-256` is the authentication mechanism.")
public class GitHubWebhookController {

    private static final long MAX_PAYLOAD_SIZE = 25 * 1024 * 1024; // 25MB — GitHub's max

    private final GitHubWebhookService webhookService;
    private final ObjectMapper objectMapper;

    /**
     * Receive GitHub webhook events.
     * GitHub sends POST requests with X-GitHub-Event, X-GitHub-Delivery, and X-Hub-Signature-256 headers.
     * The webhook row + a durable job row are persisted in one transaction and
     * the event is dispatched asynchronously by the job worker — the request
     * returns 202 Accepted. A replayed delivery (same X-GitHub-Delivery) returns
     * the recorded result with 200 and {@code replayed: true}.
     *
     * @throws WebhookPayloadTooLargeException  if payload exceeds 25MB
     * @throws WebhookSignatureInvalidException if signature verification fails
     * @throws WebhookPayloadInvalidException   if request body cannot be read or parsed
     */
    @PostMapping("/github")
    @Operation(
            summary = "Receive a GitHub webhook event",
            description = "Entry point configured in the GitHub App. Verifies the `X-Hub-Signature-256` HMAC signature against the shared secret, persists the webhook and enqueues a durable job (202), or returns the recorded result for a replayed delivery (200)."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Webhook accepted and queued",
            content = @Content(schema = @Schema(implementation = WebhookResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Replayed delivery — recorded result returned",
            content = @Content(schema = @Schema(implementation = WebhookResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid signature — event rejected",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "Payload larger than 25MB",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<WebhookResponse>> handleGitHubWebhook(
            HttpServletRequest request,
            @RequestHeader(value = "X-GitHub-Event", required = false)
            @Parameter(description = "Event type, e.g. `push`, `pull_request`, `ping`") String eventType,
            @RequestHeader(value = "X-GitHub-Delivery", required = false)
            @Parameter(description = "GitHub delivery GUID") String deliveryId,
            @RequestHeader(value = "X-Hub-Signature-256", required = false)
            @Parameter(description = "HMAC-SHA256 signature of the raw body") String signature) throws IOException {

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

        // Persist webhook + job and queue for async dispatch — exceptions propagate to GlobalExceptionHandler
        WebhookResponse response = webhookService.receiveWebhook(
                eventType, deliveryId, action, payload, installationId);

        if (Boolean.TRUE.equals(response.getReplayed())) {
            return ResponseEntity.ok(ApiResponse.success("Webhook already processed", response));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Webhook accepted for processing", response));
    }

    /**
     * Health check endpoint for webhook configuration verification.
     */
    @GetMapping("/github/health")
    @Operation(
            summary = "Webhook health check",
            description = "Returns 200 when the webhook endpoint is reachable. Used when configuring the GitHub App."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Webhook endpoint active",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    public ResponseEntity<ApiResponse<String>> webhookHealth() {
        return ResponseEntity.ok(ApiResponse.success("Webhook endpoint is active", "ok"));
    }
}
