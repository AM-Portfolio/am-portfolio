package com.portfolio.kafka.metrics;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;

/**
 * Business counters for portfolio Kafka update / create processing (Grafana table).
 */
@Component
@RequiredArgsConstructor
public class PortfolioUpdateMetrics {

    private final MeterRegistry meterRegistry;

    public void received(String source) {
        meterRegistry.counter("portfolio.update.events", "source", source, "outcome", "received").increment();
    }

    public void dedupSkipped(String source) {
        meterRegistry.counter("portfolio.update.events", "source", source, "outcome", "dedup_skipped").increment();
    }

    public void success(String source, String op) {
        meterRegistry.counter("portfolio.update.events", "source", source, "outcome", "success", "op", op).increment();
    }

    public void failed(String source, String op) {
        meterRegistry.counter("portfolio.update.events", "source", source, "outcome", "failed", "op", op).increment();
    }
}
