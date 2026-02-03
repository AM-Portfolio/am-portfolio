package com.portfolio.model.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockHoldingUpdateEvent {
    private String id; // Event ID or Holding ID
    private String userId;
    private String portfolioId;
    private String symbol;
    private BigDecimal quantity;
    private BigDecimal averagePrice;
    private BigDecimal investmentAmount;
    private LocalDateTime timestamp;
    private String updateType; // ADD, REMOVE, UPDATE

    // Performance Metrics
    private Double overallGainLoss;
    private Double overallGainLossPercentage;
    private Double totalGainLoss;
    private Double totalGainLossPercentage;

}
