package com.portfolio.kafka.config;

public final class KafkaTopics {
    private KafkaTopics() {
    }

    // Calculation Triggers
    public static final String TRIGGER_CALCULATION = "am-trigger-calculation";

    // Previous-close snapshot — emitted daily by the Market-Data-Scheduler.
    // Payload contains per-symbol close prices across 1D/1W/1M/3M/6M/1Y/5Y windows.
    public static final String PREVIOUS_CLOSE_SNAPSHOT = "am-entity-previous-close-snapshot";
}
