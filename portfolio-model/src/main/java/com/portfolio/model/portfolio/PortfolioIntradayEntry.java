package com.portfolio.model.portfolio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a specific broker portfolio's contribution to the intraday data point.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioIntradayEntry {
    private String portfolioId;
    private String portfolioName;
    private String brokerType;
    private double value;
    private double changeFromOpen;
    private double changeFromOpenPct;
}
