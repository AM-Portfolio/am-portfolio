package com.portfolio.model.portfolio;

import java.time.Instant;
import java.util.Map;

import com.portfolio.model.StockPerformanceGroup;
import com.portfolio.model.TimeInterval;

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
