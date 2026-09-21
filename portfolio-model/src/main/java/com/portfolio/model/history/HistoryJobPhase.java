package com.portfolio.model.history;

/**
 * Job phase exposed to UI / status API while chart history is building.
 */
public enum HistoryJobPhase {
    QUEUED,
    BUILDING_90D,
    BUILDING_1Y,
    READY,
    FAILED
}
