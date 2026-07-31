package com.devbraid.githubapp.controller;

import com.devbraid.common.exception.GlobalExceptionHandler;
import com.devbraid.githubapp.dto.response.WebhookResponse;
import com.devbraid.githubapp.exception.WebhookSignatureInvalidException;
import com.devbraid.githubapp.service.GitHubWebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("GitHubWebhookController Unit Tests")
@ExtendWith(MockitoExtension.class)
class GitHubWebhookControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    @Mock
    private GitHubWebhookService webhookService;
    private GitHubWebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new GitHubWebhookController(webhookService, objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/github returns 200 on valid ping")
    void handleGitHubWebhook_ping_returns200() throws Exception {
        WebhookResponse response = WebhookResponse.builder()
                .id(UUID.randomUUID())
                .installationId(12345L)
                .eventType("ping")
                .processed(true)
                .receivedAt(OffsetDateTime.now())
                .build();

        when(webhookService.verifySignature(any(byte[].class), isNull())).thenReturn(true);
        when(webhookService.processWebhook(eq("ping"), isNull(), isNull(), any(), isNull()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "ping")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Webhook processed"));
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/github returns 401 on invalid signature")
    void handleGitHubWebhook_invalidSignature_returns401() throws Exception {
        when(webhookService.verifySignature(any(byte[].class), eq("sha256=invalid")))
                .thenThrow(new WebhookSignatureInvalidException("Invalid webhook signature"));

        mockMvc.perform(post("/api/v1/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "push")
                        .header("X-Hub-Signature-256", "sha256=invalid")
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid webhook signature"));
    }

    @Test
    @DisplayName("GET /api/v1/webhooks/github/health returns 200")
    void webhookHealth_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/webhooks/github/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("ok"));
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/github returns 400 on malformed JSON")
    void handleGitHubWebhook_malformedJson_returns400() throws Exception {
        when(webhookService.verifySignature(any(byte[].class), isNull())).thenReturn(true);

        mockMvc.perform(post("/api/v1/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "push")
                        .content("not valid json {{{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/github handles pull_request event")
    void handleGitHubWebhook_pullRequest_returns200() throws Exception {
        String payload = """
                {
                    "action": "opened",
                    "repository": {"full_name": "owner/repo"},
                    "pull_request": {
                        "number": 1,
                        "title": "Test PR",
                        "head": {"ref": "feature"},
                        "base": {"ref": "main"}
                    }
                }
                """;

        WebhookResponse response = WebhookResponse.builder()
                .id(UUID.randomUUID())
                .installationId(12345L)
                .eventType("pull_request")
                .action("opened")
                .processed(true)
                .receivedAt(OffsetDateTime.now())
                .build();

        when(webhookService.verifySignature(any(byte[].class), isNull())).thenReturn(true);
        when(webhookService.processWebhook(eq("pull_request"), isNull(), eq("opened"), any(), isNull()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "pull_request")
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eventType").value("pull_request"))
                .andExpect(jsonPath("$.data.action").value("opened"));
    }
}
