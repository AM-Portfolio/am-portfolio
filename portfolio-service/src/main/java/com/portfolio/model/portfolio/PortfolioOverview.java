package com.portfolio.model.portfolio;

import java.time.Instant;
import java.util.Map;

import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PortfolioOverview {
    // Core portfolio information
    private PortfolioSummaryV1 portfolioSummary;
    
    // Metadata
    private TimeInterval currentInterval;
    private Instant lastUpdated;
}
