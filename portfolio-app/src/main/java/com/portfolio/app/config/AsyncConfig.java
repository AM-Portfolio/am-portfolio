package com.portfolio.app.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Async thread-pool configuration.
 *
 * <p>The {@link MdcTaskDecorator} ensures that the calling thread's MDC context
 * (traceId, spanId, correlationId, userId, etc.) is propagated into every worker
 * thread spawned via {@code @Async} or {@code CompletableFuture.supplyAsync(...)}.
 * Without this, trace IDs would be lost at async boundaries, breaking the
 * distributed-trace chain in Jaeger and the structured logs.
 *
 * <p>This mirrors the pattern used in {@code am-observability-lib}'s
 * {@code MdcTaskDecorator}.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(50);
        executor.setMaxPoolSize(100);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("PortfolioAsync-");
        // Propagate traceId/spanId/correlationId across async thread boundaries
        executor.setTaskDecorator(new MdcTaskDecorator());
        // If queue is full, run in caller's thread instead of throwing exception
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean(name = "externalApiExecutor")
    public Executor externalApiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ExtApiAsync-");
        // Propagate traceId/spanId/correlationId across async thread boundaries
        executor.setTaskDecorator(new MdcTaskDecorator());
        // Backpressure via CallerRunsPolicy is now safe due to shorter timeouts
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Copies the calling thread's MDC snapshot into the worker thread before
     * execution and restores the worker's original MDC afterwards.
     *
     * <p>This is a self-contained copy of the pattern in
     * {@code com.am.observability.mdc.MdcTaskDecorator} so that
     * {@code portfolio-app} has no compile-time dependency on the core-services
     * library JAR.
     */
    static class MdcTaskDecorator implements TaskDecorator {

        @Override
        public Runnable decorate(Runnable runnable) {
            // Capture all context variables including TraceContext and MDC
            io.micrometer.context.ContextSnapshot snapshot = 
                io.micrometer.context.ContextSnapshotFactory.builder().build().captureAll();
                
            return () -> {
                // Restore the context in the worker thread
                try (io.micrometer.context.ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
                    runnable.run();
                }
            };
        }
    }
}
