package com.devbraid.config;

import com.devbraid.indexing.service.IndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Production-grade executor for {@code @Async} methods (e.g. codebase indexing,
 * fire-and-forget WebSocket broadcasts). Bounded pool prevents unbounded thread
 * creation under load. {@code @EnableAsync} lives on {@code DevbraidBackendApplication}.
 * <p>
 * Also configures {@link AsyncUncaughtExceptionHandler} so that failed @Async
 * tasks (like indexing) are marked FAILED in the DB — no try/catch in the service.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AsyncConfig implements AsyncConfigurer {

    private final IndexingService indexingService;

    @Override
    @Bean(name = "taskExecutor")
    public TaskExecutor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable ex, Method method, Object... params) -> {
            log.error("Async task failed: {}.{}() — {}",
                    method.getDeclaringClass().getSimpleName(), method.getName(), ex.getMessage(), ex);

            // If the failed method is startIndexing(UUID indexId, List<String>),
            // mark the index as FAILED so it doesn't stay stuck in INDEXING status.
            if ("startIndexing".equals(method.getName()) && params.length > 0 && params[0] instanceof UUID indexId) {
                indexingService.markIndexFailed(indexId, ex.getMessage());
            }
        };
    }
}
