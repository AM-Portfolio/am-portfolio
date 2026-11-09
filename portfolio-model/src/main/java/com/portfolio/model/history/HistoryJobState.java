package com.portfolio.model.history;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis-backed per-user history job state.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryJobState {
    private String userId;
    private long generation;
    private HistoryJobMode mode;
    private String targetPortfolioId;
    private HistoryJobPhase phase;
    private HistoryStatus historyStatus;
    private Instant startedAt;
    private Instant updatedAt;
    private String historyFrom;
    private String historyTo;
    private int coverageDays;
    private String failureReason;
}
