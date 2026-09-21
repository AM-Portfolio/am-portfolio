package com.portfolio.model.history;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight poll payload for chart sync UI (no point series).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioHistoryStatusResponse {
    private HistoryStatus historyStatus;
    private HistoryJobPhase phase;
    private HistoryJobMode mode;
    private String targetPortfolioId;
    private String startedAt;
    private String historyFrom;
    private String historyTo;
    private int coverageDays;
    private String failureReason;
}
