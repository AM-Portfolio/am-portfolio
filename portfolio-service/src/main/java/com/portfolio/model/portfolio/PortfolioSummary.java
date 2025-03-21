package com.portfolio.model.portfolio;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PortfolioSummary {
    private String portfolioId;
    private double totalValue;
    private double totalCost;
    private double totalGainLoss;
    private double totalGainLossPercentage;
    private int totalAssets;
    private int gainersCount;
    private int losersCount;
    private double averageGainLossPercentage;
    private Instant lastUpdated;
}
