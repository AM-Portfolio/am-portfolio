package com.portfolio.model;

import com.portfolio.model.breakdown.MarketBreakdown;
import com.portfolio.model.breakdown.PerformanceBreakdown;
import com.portfolio.model.breakdown.SectorBreakdown;
import com.portfolio.model.enums.BrokerType;
import com.portfolio.model.enums.PlatformType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class PortfolioAnalysis {
    // Core portfolio information
    private String portfolioId;
    private String userId;
    private Instant lastUpdated;
    private BigDecimal totalValue;
    private BigDecimal totalCost;
    private BigDecimal totalGainLoss;
    private double totalReturnPercentage;
    
    // Core holdings and transactions
    private List<Holding> holdings;
    private List<Transaction> transactions;
    private List<CashFlow> cashFlows;
    
    // Detailed breakdowns
    private PerformanceBreakdown performanceBreakdown;
    private SectorBreakdown sectorBreakdown;
    private MarketBreakdown marketBreakdown;
    
    // Time-based performance metrics
    private Map<TimeInterval, PerformanceMetrics> timeBasedMetrics;
    private Map<String, Double> timeWeightedReturns; // key: timeWindow, value: TWR
    
    // Broker and platform analysis
    private Map<BrokerType, List<Holding>> holdingsByBroker;
    private Map<PlatformType, List<Holding>> holdingsByPlatform;
    private Map<BrokerType, BigDecimal> valueByBroker;
    private Map<PlatformType, BigDecimal> valueByPlatform;
    
    // Metadata
    private TimeInterval currentInterval;
    
    @Builder.Default
    private List<String> errors = new ArrayList<>();
}
