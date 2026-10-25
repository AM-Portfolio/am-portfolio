package com.portfolio.model.history;

import java.util.Collections;
import java.util.List;

import com.am.common.amcommondata.model.PortfolioSnapshotModel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chart history points plus build status metadata (serve path stays Mongo-only).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioHistoryResponse {
    @Builder.Default
    private List<PortfolioSnapshotModel> points = Collections.emptyList();
    private HistoryStatus historyStatus;
    private HistoryJobPhase phase;
    private String startedAt;
    private String historyFrom;
    private String historyTo;
    private int coverageDays;
}
