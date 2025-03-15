package com.portfolio.model;

import java.time.Instant;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PortfolioAnalysis {
    // Core portfolio information
    private String portfolioId;
    private PortfolioSummary summary;
    
    // Performance groups
    private StockPerformanceGroup gainers;
    private StockPerformanceGroup losers;

    // Time-based performance metrics
    private Map<TimeInterval, PerformanceMetrics> timeBasedMetrics;
    
    // Metadata
    private TimeInterval currentInterval;
    private Instant lastUpdated;
}
