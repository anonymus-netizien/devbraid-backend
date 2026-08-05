package com.devbraid.githubapp.service;

import com.devbraid.changethread.service.ChangeThreadService;
import com.devbraid.githubapp.entity.GitHubAppInstallation;
import com.devbraid.githubapp.entity.GitHubIdentity;
import com.devbraid.githubapp.entity.GitHubWebhook;
import com.devbraid.githubapp.entity.WebhookJob;
import com.devbraid.githubapp.exception.WebhookSignatureInvalidException;
import com.devbraid.githubapp.repository.GitHubAppInstallationRepository;
import com.devbraid.githubapp.repository.GitHubIdentityRepository;
import com.devbraid.githubapp.repository.GitHubWebhookRepository;
import com.devbraid.githubapp.repository.WebhookJobRepository;
import com.devbraid.review.service.PrReviewTriggerService;
import com.devbraid.user.entity.User;
import com.fasterxml.jackson.core.JsonProcessingException;
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
    private GitHubIdentityRepository identityRepository;
    @Mock
    private WebhookJobRepository jobRepository;
    @Mock
    private ChangeThreadService changeThreadService;
    @Mock
    private PrReviewTriggerService prReviewTriggerService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private GitHubWebhookService webhookService;

    private User testUser;
    private GitHubAppInstallation testInstallation;
    private GitHubIdentity testIdentity;

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

        testIdentity = GitHubIdentity.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .githubUserId(999L)
                .githubLogin("octocat")
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
    @DisplayName("receiveWebhook")
    class ReceiveWebhook {

        @Test
        @DisplayName("Persists webhook and enqueues a durable job without dispatching")
        void receiveWebhook_newWebhook_savesWebhookAndQueuesJob() throws Exception {
            String deliveryId = "new-delivery-456";

            GitHubWebhook saved = GitHubWebhook.builder()
                    .id(UUID.randomUUID())
                    .deliveryId(deliveryId)
                    .eventType("ping")
                    .installationId(12345L)
                    .processed(false)
                    .receivedAt(OffsetDateTime.now())
                    .build();
            when(webhookRepository.findByDeliveryId(deliveryId)).thenReturn(Optional.empty());
            when(webhookRepository.save(any(GitHubWebhook.class))).thenReturn(saved);

            JsonNode payload = objectMapper.createObjectNode();
            var response = webhookService.receiveWebhook("ping", deliveryId, null, payload, 12345L);

            assertNotNull(response);
            assertFalse(response.getReplayed());
            assertFalse(response.getProcessed());

            ArgumentCaptor<WebhookJob> jobCaptor = ArgumentCaptor.forClass(WebhookJob.class);
            verify(jobRepository).save(jobCaptor.capture());
            assertEquals(saved.getId(), jobCaptor.getValue().getWebhookId());
            assertEquals("ping", jobCaptor.getValue().getEventType());
            assertNotNull(jobCaptor.getValue().getNextAttemptAt());
            verify(webhookRepository, times(1)).save(any(GitHubWebhook.class));
        }

        @Test
        @DisplayName("Returns recorded result with replayed=true for a duplicate delivery id")
        void receiveWebhook_duplicateDelivery_returnsReplay() throws Exception {
            String deliveryId = "duplicate-delivery-999";
            GitHubWebhook existing = GitHubWebhook.builder()
                    .id(UUID.randomUUID())
                    .deliveryId(deliveryId)
                    .eventType("push")
                    .installationId(12345L)
                    .processed(true)
                    .receivedAt(OffsetDateTime.now())
                    .processedAt(OffsetDateTime.now())
                    .build();
            when(webhookRepository.findByDeliveryId(deliveryId)).thenReturn(Optional.of(existing));

            JsonNode payload = objectMapper.createObjectNode();
            var response = webhookService.receiveWebhook("push", deliveryId, null, payload, 12345L);

            assertNotNull(response);
            assertTrue(response.getReplayed());
            assertEquals(existing.getId(), response.getId());
            verify(webhookRepository, never()).save(any(GitHubWebhook.class));
            verify(jobRepository, never()).save(any(WebhookJob.class));
        }

        @Test
        @DisplayName("Throws JsonProcessingException when payload serialization fails")
        void receiveWebhook_serializationFails_throwsException() throws Exception {
            String deliveryId = "bad-payload";
            when(webhookRepository.findByDeliveryId(deliveryId)).thenReturn(Optional.empty());
            JsonNode badPayload = mock(JsonNode.class);
            doThrow(new JsonProcessingException("fail") {
            }).when(objectMapper).writeValueAsString(badPayload);

            assertThrows(JsonProcessingException.class,
                    () -> webhookService.receiveWebhook("push", deliveryId, null, badPayload, 12345L));
        }
    }

    @Nested
    @DisplayName("Event Dispatching (job worker path)")
    class EventDispatching {

        @Test
        @DisplayName("Handles pull_request opened event and auto-creates thread")
        void dispatch_pullRequestOpened_createsThread() {
            when(installationRepository.findByInstallationId(12345L))
                    .thenReturn(Optional.of(testInstallation));

            com.devbraid.changethread.dto.response.ThreadResponse threadResponse =
                    com.devbraid.changethread.dto.response.ThreadResponse.builder()
                            .id(UUID.randomUUID())
                            .title("Test PR")
                            .build();
            when(changeThreadService.createThreadForInstallation(eq(testUser), eq(12345L), any())).thenReturn(threadResponse);

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
            headNode.put("sha", "sha123");
            prNode.set("head", headNode);
            com.fasterxml.jackson.databind.node.ObjectNode baseNode = objectMapper.createObjectNode();
            baseNode.put("ref", "main");
            prNode.set("base", baseNode);
            payload.set("pull_request", prNode);

            webhookService.dispatch("pull_request", "opened", payload, 12345L);

            verify(changeThreadService).createThreadForInstallation(eq(testUser), eq(12345L), any());
            verify(prReviewTriggerService).triggerWebhookReview(
                    eq(testUser), eq(threadResponse.getId()), eq(42), eq("sha123"), eq(12345L));
        }

        @Test
        @DisplayName("Handles push event without error")
        void dispatch_pushEvent_logsSuccessfully() {
            com.fasterxml.jackson.databind.node.ObjectNode payload = objectMapper.createObjectNode();
            payload.put("ref", "refs/heads/main");
            com.fasterxml.jackson.databind.node.ObjectNode repoNode = objectMapper.createObjectNode();
            repoNode.put("full_name", "owner/repo");
            payload.set("repository", repoNode);
            payload.set("commits", objectMapper.createArrayNode());

            webhookService.dispatch("push", null, payload, 12345L);

            verify(changeThreadService, never()).createThreadForInstallation(any(), any(), any());
        }

        @Test
        @DisplayName("Handles ping event")
        void dispatch_pingEvent_handled() {
            JsonNode payload = objectMapper.createObjectNode();
            webhookService.dispatch("ping", null, payload, 12345L);
            verify(changeThreadService, never()).createThreadForInstallation(any(), any(), any());
        }

        @Test
        @DisplayName("Handles pull_request_review submitted event")
        void dispatch_prReviewSubmitted_handled() {
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

            webhookService.dispatch("pull_request_review", "submitted", payload, 12345L);

            verify(changeThreadService, never()).createThreadForInstallation(any(), any(), any());
        }

        @Test
        @DisplayName("Ignores pull_request actions other than opened/synchronize")
        void dispatch_prClosed_ignored() {
            com.fasterxml.jackson.databind.node.ObjectNode payload = objectMapper.createObjectNode();
            payload.put("action", "closed");

            webhookService.dispatch("pull_request", "closed", payload, 12345L);

            verify(changeThreadService, never()).createThreadForInstallation(any(), any(), any());
        }

        @Test
        @DisplayName("Skips thread creation when no user found for installation")
        void dispatch_noUserForInstallation_skipsThreadCreation() {
            when(installationRepository.findByInstallationId(12345L))
                    .thenReturn(Optional.empty());

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

            webhookService.dispatch("pull_request", "opened", payload, 12345L);

            verify(changeThreadService, never()).createThreadForInstallation(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Installation Persistence")
    class InstallationPersistence {

        private com.fasterxml.jackson.databind.node.ObjectNode installationPayload(
                String action, long installationId, long senderId, long accountId) {
            com.fasterxml.jackson.databind.node.ObjectNode payload = objectMapper.createObjectNode();
            payload.put("action", action);
            com.fasterxml.jackson.databind.node.ObjectNode installationNode = objectMapper.createObjectNode();
            installationNode.put("id", installationId);
            com.fasterxml.jackson.databind.node.ObjectNode accountNode = objectMapper.createObjectNode();
            accountNode.put("id", accountId);
            accountNode.put("login", "acme-corp");
            accountNode.put("type", "Organization");
            installationNode.set("account", accountNode);
            installationNode.put("repository_selection", "selected");
            payload.set("installation", installationNode);
            com.fasterxml.jackson.databind.node.ObjectNode senderNode = objectMapper.createObjectNode();
            senderNode.put("id", senderId);
            payload.set("sender", senderNode);
            return payload;
        }

        @Test
        @DisplayName("created with a linked identity inserts an installation row")
        void dispatch_installationCreated_withIdentity_insertsRow() {
            // sender id 999 matches an identity — the account-id fallback is never consulted
            when(identityRepository.findByGithubUserId(999L)).thenReturn(Optional.of(testIdentity));
            when(installationRepository.findByInstallationId(12345L)).thenReturn(Optional.empty());

            webhookService.dispatch("installation", "created",
                    installationPayload("created", 12345L, 999L, 777L), null);

            ArgumentCaptor<GitHubAppInstallation> captor = ArgumentCaptor.forClass(GitHubAppInstallation.class);
            verify(installationRepository).save(captor.capture());
            GitHubAppInstallation saved = captor.getValue();
            assertEquals(12345L, saved.getInstallationId());
            assertEquals(testUser, saved.getUser());
            assertEquals("acme-corp", saved.getAccountLogin());
            assertEquals("Organization", saved.getAccountType());
            assertEquals("selected", saved.getRepositorySelection());
            assertTrue(saved.getActive());
            assertFalse(saved.getSuspended());
        }

        @Test
        @DisplayName("created matches identity on the account id when sender is unknown")
        void dispatch_installationCreated_matchesOnAccountId() {
            when(identityRepository.findByGithubUserId(111L)).thenReturn(Optional.empty());
            when(identityRepository.findByGithubUserId(999L)).thenReturn(Optional.of(testIdentity));
            when(installationRepository.findByInstallationId(12345L)).thenReturn(Optional.empty());

            webhookService.dispatch("installation", "created",
                    installationPayload("created", 12345L, 111L, 999L), null);

            verify(installationRepository).save(any(GitHubAppInstallation.class));
        }

        @Test
        @DisplayName("created without a linked identity skips persistence")
        void dispatch_installationCreated_noIdentity_skipsRow() {
            when(identityRepository.findByGithubUserId(111L)).thenReturn(Optional.empty());
            when(identityRepository.findByGithubUserId(222L)).thenReturn(Optional.empty());

            webhookService.dispatch("installation", "created",
                    installationPayload("created", 12345L, 111L, 222L), null);

            verify(installationRepository, never()).save(any(GitHubAppInstallation.class));
        }

        @Test
        @DisplayName("created reactivates an existing installation row")
        void dispatch_installationCreated_existingRow_reactivates() {
            testInstallation.setActive(false);
            testInstallation.setSuspended(true);
            when(identityRepository.findByGithubUserId(999L)).thenReturn(Optional.of(testIdentity));
            when(installationRepository.findByInstallationId(12345L))
                    .thenReturn(Optional.of(testInstallation));

            webhookService.dispatch("installation", "created",
                    installationPayload("created", 12345L, 999L, 999L), null);

            ArgumentCaptor<GitHubAppInstallation> captor = ArgumentCaptor.forClass(GitHubAppInstallation.class);
            verify(installationRepository).save(captor.capture());
            assertTrue(captor.getValue().getActive());
            assertFalse(captor.getValue().getSuspended());
        }

        @Test
        @DisplayName("deleted deactivates the installation row")
        void dispatch_installationDeleted_deactivates() {
            when(installationRepository.findByInstallationId(12345L))
                    .thenReturn(Optional.of(testInstallation));

            webhookService.dispatch("installation", "deleted",
                    installationPayload("deleted", 12345L, 999L, 999L), null);

            ArgumentCaptor<GitHubAppInstallation> captor = ArgumentCaptor.forClass(GitHubAppInstallation.class);
            verify(installationRepository).save(captor.capture());
            assertFalse(captor.getValue().getActive());
        }

        @Test
        @DisplayName("suspend marks the installation suspended")
        void dispatch_installationSuspended_marksSuspended() {
            when(installationRepository.findByInstallationId(12345L))
                    .thenReturn(Optional.of(testInstallation));

            webhookService.dispatch("installation", "suspend",
                    installationPayload("suspend", 12345L, 999L, 999L), null);

            ArgumentCaptor<GitHubAppInstallation> captor = ArgumentCaptor.forClass(GitHubAppInstallation.class);
            verify(installationRepository).save(captor.capture());
            assertTrue(captor.getValue().getSuspended());
            assertNotNull(captor.getValue().getSuspendedAt());
        }

        @Test
        @DisplayName("unsuspend clears the suspended flag")
        void dispatch_installationUnsuspended_clearsSuspended() {
            testInstallation.setSuspended(true);
            testInstallation.setSuspendedAt(OffsetDateTime.now());
            when(installationRepository.findByInstallationId(12345L))
                    .thenReturn(Optional.of(testInstallation));

            webhookService.dispatch("installation", "unsuspend",
                    installationPayload("unsuspend", 12345L, 999L, 999L), null);

            ArgumentCaptor<GitHubAppInstallation> captor = ArgumentCaptor.forClass(GitHubAppInstallation.class);
            verify(installationRepository).save(captor.capture());
            assertFalse(captor.getValue().getSuspended());
            assertNull(captor.getValue().getSuspendedAt());
        }
    }
}
