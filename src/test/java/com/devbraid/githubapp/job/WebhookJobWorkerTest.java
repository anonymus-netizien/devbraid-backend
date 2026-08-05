package com.devbraid.githubapp.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;

@DisplayName("WebhookJobWorker Unit Tests")
@ExtendWith(MockitoExtension.class)
class WebhookJobWorkerTest {

    @Mock
    private WebhookJobProcessor jobProcessor;

    private WebhookJobWorker worker;

    @BeforeEach
    void setUp() {
        worker = new WebhookJobWorker(jobProcessor);
        ReflectionTestUtils.setField(worker, "batchSize", 5);
    }

    @Test
    @DisplayName("processDueJobs claims up to the batch size")
    void processDueJobs_claimsUpToBatchSize() {
        when(jobProcessor.processOne()).thenReturn(true);

        worker.processDueJobs();

        verify(jobProcessor, times(5)).processOne();
    }

    @Test
    @DisplayName("processDueJobs stops early when the queue is empty")
    void processDueJobs_stopsWhenQueueEmpty() {
        when(jobProcessor.processOne()).thenReturn(true, true, false);

        worker.processDueJobs();

        verify(jobProcessor, times(3)).processOne();
    }

    @Test
    @DisplayName("processDueJobs keeps polling when a claim/processing transaction fails")
    void processDueJobs_swallowsProcessorFailure() {
        doThrow(new RuntimeException("db down"))
                .doReturn(true)
                .doReturn(true)
                .doReturn(false)
                .when(jobProcessor).processOne();

        worker.processDueJobs();

        verify(jobProcessor, times(4)).processOne();
    }
}
