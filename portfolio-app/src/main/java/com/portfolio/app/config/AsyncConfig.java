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
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("PortfolioAsync-");
        // Propagate traceId/spanId/correlationId across async thread boundaries
        executor.setTaskDecorator(new MdcTaskDecorator());
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
            // Capture the MDC of the submitting thread at scheduling time
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            return () -> {
                // Save whatever the worker thread already has (may be empty)
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                } else {
                    MDC.clear();
                }
                try {
                    runnable.run();
                } finally {
                    // Restore the worker thread's original MDC
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    } else {
                        MDC.clear();
                    }
                }
            };
        }
    }
}
