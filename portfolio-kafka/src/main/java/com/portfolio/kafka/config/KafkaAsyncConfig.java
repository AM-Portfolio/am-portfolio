package com.portfolio.kafka.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;

@Configuration
public class KafkaAsyncConfig {

    @Autowired(required = false)
    private MeterRegistry registry;

    @Bean(name = "historyWriterExecutor")
    public Executor historyWriterExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(10);
        ex.setMaxPoolSize(50);
        ex.setQueueCapacity(10_000);
        ex.setThreadNamePrefix("history-writer-");
        // Reject policy: CallerRunsPolicy means if the queue is full, the calling thread
        // (the Kafka consumer thread) will execute the task directly, applying backpressure
        // without data loss.
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.initialize();

        if (registry != null && ex.getThreadPoolExecutor() != null) {
            ExecutorServiceMetrics.monitor(registry, ex.getThreadPoolExecutor(), "history_writer_pool",
                Tags.of("module", "kafka"));
        }

        return ex;
    }
}
