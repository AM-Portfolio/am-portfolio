package com.portfolio.model.history;

/**
 * Modes for asynchronous portfolio chart history builds.
 */
public enum HistoryJobMode {
    /** Replace nested entry for target portfolio (or bootstrap) and recompute All. */
    FULL,
    /** Upsert nested entry for a newly added broker without wiping other brokers. */
    MERGE_BROKER,
    /** Fill missing days only (login / inactivity). */
    GAP
}
