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
    private List<StockPerformance> topFiveGainers; // Default top 5
    private List<StockPerformance> topFiveLosers; // Default top 5
    private PaginatedStockPerformance gainers; // Paginated full list
    private PaginatedStockPerformance losers; // Paginated full list
    private Instant lastUpdated;
}
