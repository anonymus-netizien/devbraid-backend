package com.devbraid.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AsyncConfig Unit Tests")
class AsyncConfigTest {

    private final AsyncConfig config = new AsyncConfig();

    @Test
    @DisplayName("taskExecutor is a bounded ThreadPoolTaskExecutor with async- prefix")
    void taskExecutor_isThreadPoolExecutor_withExpectedName() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.taskExecutor();
        assertThat(executor.getThreadNamePrefix()).isEqualTo("async-");
        assertThat(executor.getMaxPoolSize()).isEqualTo(8);
        assertThat(executor.getCorePoolSize()).isEqualTo(4);
        assertThat(executor.getQueueCapacity()).isEqualTo(100);
        executor.shutdown();
    }
}
