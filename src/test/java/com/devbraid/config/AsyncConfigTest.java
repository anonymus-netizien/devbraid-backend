package com.devbraid.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncConfig Unit Tests")
class AsyncConfigTest {

    @InjectMocks
    private AsyncConfig config;

    @Test
    @DisplayName("getAsyncExecutor returns a bounded ThreadPoolTaskExecutor with async- prefix")
    void taskExecutor_isThreadPoolExecutor_withExpectedName() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.getAsyncExecutor();
        assertThat(executor.getThreadNamePrefix()).isEqualTo("async-");
        assertThat(executor.getMaxPoolSize()).isEqualTo(8);
        assertThat(executor.getCorePoolSize()).isEqualTo(4);
        assertThat(executor.getQueueCapacity()).isEqualTo(100);
        executor.shutdown();
    }

    @Test
    @DisplayName("getAsyncUncaughtExceptionHandler returns a non-null handler")
    void asyncUncaughtExceptionHandler_isNotNull() {
        assertThat(config.getAsyncUncaughtExceptionHandler()).isNotNull();
    }
}
