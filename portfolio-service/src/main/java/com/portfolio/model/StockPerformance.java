package com.portfolio.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StockPerformance {
    private String symbol;
    private double quantity;
    private double currentPrice;
    private double averagePrice;
    private double gainLoss;
    private double gainLossPercentage;
}
