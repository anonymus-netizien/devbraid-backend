package com.devbraid.githubapp.job;

import com.devbraid.githubapp.entity.GitHubWebhook;
import com.devbraid.githubapp.entity.JobStatus;
import com.devbraid.githubapp.entity.WebhookJob;
import com.devbraid.githubapp.repository.GitHubWebhookRepository;
import com.devbraid.githubapp.repository.WebhookJobRepository;
import com.devbraid.githubapp.service.GitHubWebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("WebhookJobProcessor Unit Tests")
@ExtendWith(MockitoExtension.class)
class WebhookJobProcessorTest {

    @Mock
    private WebhookJobRepository jobRepository;
    @Mock
    private GitHubWebhookRepository webhookRepository;
    @Mock
    private GitHubWebhookService webhookService;

    @InjectMocks
    private WebhookJobProcessor processor;

    private WebhookJob testJob;
    private GitHubWebhook testWebhook;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(processor, "maxAttempts", 5);
        ReflectionTestUtils.setField(processor, "backoffBaseMs", 2000L);
        ReflectionTestUtils.setField(processor, "objectMapper", new ObjectMapper());

        testWebhook = GitHubWebhook.builder()
                .id(UUID.randomUUID())
                .installationId(12345L)
                .eventType("pull_request")
                .action("opened")
                .payload("{}")
                .processed(false)
                .build();

        testJob = WebhookJob.builder()
                .id(UUID.randomUUID())
                .webhookId(testWebhook.getId())
                .eventType("pull_request")
                .payload("{}")
                .status(JobStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(OffsetDateTime.now().minusSeconds(1))
                .build();
    }

    @Test
    @DisplayName("processOne returns false when the queue is empty")
    void processOne_noJobs_returnsFalse() {
        when(jobRepository.claimBatch(1)).thenReturn(List.of());

        assertThat(processor.processOne()).isFalse();
        verify(jobRepository, never()).save(any(WebhookJob.class));
    }

    @Test
    @DisplayName("processOne dispatches the job and marks it SUCCEEDED, marking the webhook processed")
    void processOne_success_marksSucceeded() {
        when(jobRepository.claimBatch(1)).thenReturn(List.of(testJob));
        when(webhookRepository.findById(testWebhook.getId())).thenReturn(Optional.of(testWebhook));

        boolean processed = processor.processOne();

        assertThat(processed).isTrue();
        verify(webhookService).dispatch(eq("pull_request"), eq("opened"), any(), eq(12345L));
        assertThat(testJob.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(testJob.getAttempts()).isZero();
        assertThat(testWebhook.getProcessed()).isTrue();
        assertThat(testWebhook.getProcessedAt()).isNotNull();
        verify(jobRepository).save(testJob);
        verify(webhookRepository).save(testWebhook);
    }

    @Test
    @DisplayName("processOne records the failure and schedules a 2s backoff retry")
    void processOne_failure_schedulesBackoff() {
        when(jobRepository.claimBatch(1)).thenReturn(List.of(testJob));
        when(webhookRepository.findById(testWebhook.getId())).thenReturn(Optional.of(testWebhook));
        doThrow(new RuntimeException("boom")).when(webhookService).dispatch(any(), any(), any(), any());

        boolean processed = processor.processOne();

        assertThat(processed).isTrue();
        assertThat(testJob.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(testJob.getAttempts()).isEqualTo(1);
        assertThat(testJob.getLastError()).isEqualTo("boom");
        OffsetDateTime expectedNext = OffsetDateTime.now().plusSeconds(2);
        assertThat(testJob.getNextAttemptAt()).isAfter(expectedNext.minusSeconds(2));
        assertThat(testJob.getNextAttemptAt()).isBefore(expectedNext.plusSeconds(2));
        verify(webhookRepository, never()).save(any(GitHubWebhook.class));
    }

    @Test
    @DisplayName("processOne doubles the backoff on subsequent failures")
    void processOne_failure_doublesBackoff() {
        testJob.setAttempts(1);
        when(jobRepository.claimBatch(1)).thenReturn(List.of(testJob));
        when(webhookRepository.findById(testWebhook.getId())).thenReturn(Optional.of(testWebhook));
        doThrow(new RuntimeException("boom")).when(webhookService).dispatch(any(), any(), any(), any());

        processor.processOne();

        assertThat(testJob.getAttempts()).isEqualTo(2);
        OffsetDateTime expectedNext = OffsetDateTime.now().plusSeconds(4);
        assertThat(testJob.getNextAttemptAt()).isAfter(expectedNext.minusSeconds(2));
        assertThat(testJob.getNextAttemptAt()).isBefore(expectedNext.plusSeconds(2));
    }

    @Test
    @DisplayName("processOne dead-letters the job after max attempts with last_error set")
    void processOne_failure_atMaxAttempts_deadLetters() {
        testJob.setAttempts(4);
        when(jobRepository.claimBatch(1)).thenReturn(List.of(testJob));
        when(webhookRepository.findById(testWebhook.getId())).thenReturn(Optional.of(testWebhook));
        doThrow(new RuntimeException("boom")).when(webhookService).dispatch(any(), any(), any(), any());

        processor.processOne();

        assertThat(testJob.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(testJob.getAttempts()).isEqualTo(5);
        assertThat(testJob.getLastError()).isEqualTo("boom");
        verify(jobRepository).save(testJob);
    }

    @Test
    @DisplayName("processOne treats a missing webhook row as a retryable failure")
    void processOne_missingWebhook_retriesWithBackoff() {
        when(jobRepository.claimBatch(1)).thenReturn(List.of(testJob));
        when(webhookRepository.findById(testWebhook.getId())).thenReturn(Optional.empty());

        processor.processOne();

        assertThat(testJob.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(testJob.getAttempts()).isEqualTo(1);
        assertThat(testJob.getLastError()).contains("Webhook not found");
    }
}
