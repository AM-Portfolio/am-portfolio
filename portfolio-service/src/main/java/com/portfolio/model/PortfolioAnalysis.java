package com.portfolio.model;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PortfolioAnalysis {
    private String portfolioId;
    private String userId;
    private double totalValue;
    private double totalGainLoss;
    private double totalGainLossPercentage;
    private List<StockPerformance> topGainers;
    private List<StockPerformance> topLosers;
    private Instant lastUpdated;
}
