package com.devbraid.githubapp.service;

import com.devbraid.changethread.service.ChangeThreadService;
import com.devbraid.githubapp.entity.GitHubAppInstallation;
import com.devbraid.githubapp.entity.GitHubWebhook;
import com.devbraid.githubapp.exception.WebhookNotFoundException;
import com.devbraid.githubapp.exception.WebhookPayloadInvalidException;
import com.devbraid.githubapp.exception.WebhookSignatureInvalidException;
import com.devbraid.githubapp.repository.GitHubAppInstallationRepository;
import com.devbraid.githubapp.repository.GitHubWebhookRepository;
import com.devbraid.user.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("GitHubWebhookService Unit Tests")
@ExtendWith(MockitoExtension.class)
class GitHubWebhookServiceTest {

    @Mock
    private GitHubWebhookRepository webhookRepository;
    @Mock
    private GitHubAppInstallationRepository installationRepository;
    @Mock
    private ChangeThreadService changeThreadService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private GitHubWebhookService webhookService;

    private User testUser;
    private GitHubAppInstallation testInstallation;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
                .fullName("Test User")
                .email("test@example.com")
                .build();

        testInstallation = GitHubAppInstallation.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .installationId(12345L)
                .accountLogin("test-org")
                .accountType("Organization")
                .build();
    }

    @Nested
    @DisplayName("Signature Verification")
    class SignatureVerification {

        @Test
        @DisplayName("Throws when webhook secret is not configured")
        void verifySignature_noSecret_throwsException() {
            ReflectionTestUtils.setField(webhookService, "webhookSecret", "");
            assertThrows(WebhookSignatureInvalidException.class,
                    () -> webhookService.verifySignature("payload".getBytes(), "sha256=abc"));
        }

        @Test
        @DisplayName("Returns false for null signature")
        void verifySignature_nullSignature_returnsFalse() {
            ReflectionTestUtils.setField(webhookService, "webhookSecret", "test-secret");
            assertFalse(webhookService.verifySignature("payload".getBytes(), null));
        }

        @Test
        @DisplayName("Returns false for signature without sha256= prefix")
        void verifySignature_wrongPrefix_returnsFalse() {
            ReflectionTestUtils.setField(webhookService, "webhookSecret", "test-secret");
            assertFalse(webhookService.verifySignature("payload".getBytes(), "md5=abc123"));
        }

        @Test
        @DisplayName("Returns false for invalid signature")
        void verifySignature_invalidSignature_returnsFalse() {
            ReflectionTestUtils.setField(webhookService, "webhookSecret", "test-secret");
            assertFalse(webhookService.verifySignature("payload".getBytes(), "sha256=invalidsignature"));
        }
    }

    @Nested
    @DisplayName("processWebhook")
    class ProcessWebhook {

        @Test
        @DisplayName("Deduplicates concurrent webhook via DataIntegrityViolationException")
        void processWebhook_concurrentDuplicate_handlesGracefully() {
            String deliveryId = "test-delivery-123";
            GitHubWebhook existing = GitHubWebhook.builder()
                    .id(UUID.randomUUID())
                    .deliveryId(deliveryId)
                    .eventType("push")
                    .processed(true)
                    .receivedAt(OffsetDateTime.now())
                    .build();

            when(webhookRepository.save(any(GitHubWebhook.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"));
            when(webhookRepository.findByDeliveryId(deliveryId)).thenReturn(Optional.of(existing));

            JsonNode payload = objectMapper.createObjectNode();
            var response = webhookService.processWebhook("push", deliveryId, null, payload, 12345L);

            assertNotNull(response);
            assertEquals(deliveryId, response.getDeliveryId());
        }

        @Test
        @DisplayName("Throws WebhookNotFoundException when concurrent duplicate not found")
        void processWebhook_concurrentDuplicateNotFound_throwsException() {
            String deliveryId = "missing-delivery";
            when(webhookRepository.save(any(GitHubWebhook.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"));
            when(webhookRepository.findByDeliveryId(deliveryId)).thenReturn(Optional.empty());

            JsonNode payload = objectMapper.createObjectNode();
            assertThrows(WebhookNotFoundException.class,
                    () -> webhookService.processWebhook("push", deliveryId, null, payload, 12345L));
        }

        @Test
        @DisplayName("Processes new webhook and stores it")
        void processWebhook_newWebhook_savesAndProcesses() throws Exception {
            String deliveryId = "new-delivery-456";

            GitHubWebhook saved = GitHubWebhook.builder()
                    .id(UUID.randomUUID())
                    .deliveryId(deliveryId)
                    .eventType("ping")
                    .installationId(12345L)
                    .processed(false)
                    .receivedAt(OffsetDateTime.now())
                    .build();
            when(webhookRepository.save(any(GitHubWebhook.class))).thenReturn(saved);

            JsonNode payload = objectMapper.createObjectNode();
            var response = webhookService.processWebhook("ping", deliveryId, null, payload, 12345L);

            assertNotNull(response);
            ArgumentCaptor<GitHubWebhook> captor = ArgumentCaptor.forClass(GitHubWebhook.class);
            verify(webhookRepository, times(2)).save(captor.capture());
            assertTrue(captor.getAllValues().get(1).getProcessed());
        }

        @Test
        @DisplayName("Throws WebhookPayloadInvalidException when payload serialization fails")
        void processWebhook_serializationFails_throwsException() throws Exception {
            String deliveryId = "bad-payload";
            JsonNode badPayload = mock(JsonNode.class);
            doThrow(new com.fasterxml.jackson.core.JsonProcessingException("fail") {
            }).when(objectMapper).writeValueAsString(badPayload);

            assertThrows(WebhookPayloadInvalidException.class,
                    () -> webhookService.processWebhook("push", deliveryId, null, badPayload, 12345L));
        }
    }

    @Nested
    @DisplayName("Event Dispatching")
    class EventDispatching {

        @Test
        @DisplayName("Handles pull_request opened event and auto-creates thread")
        void processWebhook_pullRequestOpened_createsThread() throws Exception {
            String deliveryId = "pr-delivery-001";
            when(installationRepository.findByInstallationId(12345L))
                    .thenReturn(Optional.of(testInstallation));

            GitHubWebhook saved = GitHubWebhook.builder()
                    .id(UUID.randomUUID())
                    .deliveryId(deliveryId)
                    .eventType("pull_request")
                    .installationId(12345L)
                    .processed(false)
                    .receivedAt(OffsetDateTime.now())
                    .build();
            when(webhookRepository.save(any(GitHubWebhook.class))).thenReturn(saved);

            com.devbraid.changethread.dto.response.ThreadResponse threadResponse =
                    com.devbraid.changethread.dto.response.ThreadResponse.builder()
                            .id(UUID.randomUUID())
                            .title("Test PR")
                            .build();
            when(changeThreadService.createThread(eq(testUser), any())).thenReturn(threadResponse);

            com.fasterxml.jackson.databind.node.ObjectNode payload = objectMapper.createObjectNode();
            payload.put("action", "opened");
            com.fasterxml.jackson.databind.node.ObjectNode repoNode = objectMapper.createObjectNode();
            repoNode.put("full_name", "owner/repo");
            com.fasterxml.jackson.databind.node.ObjectNode ownerNode = objectMapper.createObjectNode();
            ownerNode.put("login", "owner");
            repoNode.set("owner", ownerNode);
            repoNode.put("name", "repo");
            payload.set("repository", repoNode);
            com.fasterxml.jackson.databind.node.ObjectNode prNode = objectMapper.createObjectNode();
            prNode.put("number", 42);
            prNode.put("title", "Test PR");
            prNode.put("body", "PR description");
            com.fasterxml.jackson.databind.node.ObjectNode headNode = objectMapper.createObjectNode();
            headNode.put("ref", "feature-branch");
            prNode.set("head", headNode);
            com.fasterxml.jackson.databind.node.ObjectNode baseNode = objectMapper.createObjectNode();
            baseNode.put("ref", "main");
            prNode.set("base", baseNode);
            payload.set("pull_request", prNode);

            webhookService.processWebhook("pull_request", deliveryId, "opened", payload, 12345L);

            verify(changeThreadService).createThread(eq(testUser), any());
        }

        @Test
        @DisplayName("Handles push event without error")
        void processWebhook_pushEvent_logsSuccessfully() throws Exception {
            String deliveryId = "push-delivery-002";

            GitHubWebhook saved = GitHubWebhook.builder()
                    .id(UUID.randomUUID())
                    .deliveryId(deliveryId)
                    .eventType("push")
                    .installationId(12345L)
                    .processed(false)
                    .receivedAt(OffsetDateTime.now())
                    .build();
            when(webhookRepository.save(any(GitHubWebhook.class))).thenReturn(saved);

            com.fasterxml.jackson.databind.node.ObjectNode payload = objectMapper.createObjectNode();
            payload.put("ref", "refs/heads/main");
            com.fasterxml.jackson.databind.node.ObjectNode repoNode = objectMapper.createObjectNode();
            repoNode.put("full_name", "owner/repo");
            payload.set("repository", repoNode);
            payload.set("commits", objectMapper.createArrayNode());

            var response = webhookService.processWebhook("push", deliveryId, null, payload, 12345L);

            assertNotNull(response);
            assertTrue(response.getProcessed());
            verify(changeThreadService, never()).createThread(any(), any());
        }

        @Test
        @DisplayName("Handles ping event")
        void processWebhook_pingEvent_handled() throws Exception {
            String deliveryId = "ping-delivery-003";

            GitHubWebhook saved = GitHubWebhook.builder()
                    .id(UUID.randomUUID())
                    .deliveryId(deliveryId)
                    .eventType("ping")
                    .installationId(12345L)
                    .processed(false)
                    .receivedAt(OffsetDateTime.now())
                    .build();
            when(webhookRepository.save(any(GitHubWebhook.class))).thenReturn(saved);

            JsonNode payload = objectMapper.createObjectNode();
            var response = webhookService.processWebhook("ping", deliveryId, null, payload, 12345L);

            assertNotNull(response);
            assertTrue(response.getProcessed());
        }

        @Test
        @DisplayName("Handles pull_request_review submitted event")
        void processWebhook_prReviewSubmitted_handled() throws Exception {
            String deliveryId = "review-delivery-004";

            GitHubWebhook saved = GitHubWebhook.builder()
                    .id(UUID.randomUUID())
                    .deliveryId(deliveryId)
                    .eventType("pull_request_review")
                    .installationId(12345L)
                    .processed(false)
                    .receivedAt(OffsetDateTime.now())
                    .build();
            when(webhookRepository.save(any(GitHubWebhook.class))).thenReturn(saved);

            com.fasterxml.jackson.databind.node.ObjectNode payload = objectMapper.createObjectNode();
            payload.put("action", "submitted");
            com.fasterxml.jackson.databind.node.ObjectNode reviewNode = objectMapper.createObjectNode();
            reviewNode.put("state", "approved");
            payload.set("review", reviewNode);
            com.fasterxml.jackson.databind.node.ObjectNode prNode = objectMapper.createObjectNode();
            prNode.put("number", 42);
            payload.set("pull_request", prNode);
            com.fasterxml.jackson.databind.node.ObjectNode repoNode = objectMapper.createObjectNode();
            repoNode.put("full_name", "owner/repo");
            payload.set("repository", repoNode);

            var response = webhookService.processWebhook("pull_request_review", deliveryId, "submitted", payload, 12345L);

            assertNotNull(response);
            assertTrue(response.getProcessed());
        }

        @Test
        @DisplayName("Ignores pull_request actions other than opened/synchronize")
        void processWebhook_prClosed_ignored() throws Exception {
            String deliveryId = "pr-closed-delivery-005";

            GitHubWebhook saved = GitHubWebhook.builder()
                    .id(UUID.randomUUID())
                    .deliveryId(deliveryId)
                    .eventType("pull_request")
                    .installationId(12345L)
                    .processed(false)
                    .receivedAt(OffsetDateTime.now())
                    .build();
            when(webhookRepository.save(any(GitHubWebhook.class))).thenReturn(saved);

            com.fasterxml.jackson.databind.node.ObjectNode payload = objectMapper.createObjectNode();
            payload.put("action", "closed");

            var response = webhookService.processWebhook("pull_request", deliveryId, "closed", payload, 12345L);

            assertNotNull(response);
            assertTrue(response.getProcessed());
            verify(changeThreadService, never()).createThread(any(), any());
        }

        @Test
        @DisplayName("Skips thread creation when no user found for installation")
        void processWebhook_noUserForInstallation_skipsThreadCreation() throws Exception {
            String deliveryId = "pr-no-user-006";
            when(installationRepository.findByInstallationId(12345L))
                    .thenReturn(Optional.empty());

            GitHubWebhook saved = GitHubWebhook.builder()
                    .id(UUID.randomUUID())
                    .deliveryId(deliveryId)
                    .eventType("pull_request")
                    .installationId(12345L)
                    .processed(false)
                    .receivedAt(OffsetDateTime.now())
                    .build();
            when(webhookRepository.save(any(GitHubWebhook.class))).thenReturn(saved);

            com.fasterxml.jackson.databind.node.ObjectNode payload = objectMapper.createObjectNode();
            payload.put("action", "opened");
            com.fasterxml.jackson.databind.node.ObjectNode repoNode = objectMapper.createObjectNode();
            repoNode.put("full_name", "owner/repo");
            payload.set("repository", repoNode);
            com.fasterxml.jackson.databind.node.ObjectNode prNode = objectMapper.createObjectNode();
            prNode.put("number", 1);
            prNode.put("title", "PR");
            com.fasterxml.jackson.databind.node.ObjectNode headNode = objectMapper.createObjectNode();
            headNode.put("ref", "feature");
            prNode.set("head", headNode);
            com.fasterxml.jackson.databind.node.ObjectNode baseNode = objectMapper.createObjectNode();
            baseNode.put("ref", "main");
            prNode.set("base", baseNode);
            payload.set("pull_request", prNode);

            webhookService.processWebhook("pull_request", deliveryId, "opened", payload, 12345L);

            verify(changeThreadService, never()).createThread(any(), any());
        }
    }
}
