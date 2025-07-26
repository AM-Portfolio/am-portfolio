package com.portfolio.model.market;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;

import com.portfolio.model.TimeInterval;

/**
 * Represents a comparison between portfolio performance and market indices
 */
@Data
@Builder
public class MarketComparison {
    private String indexSymbol;
    private double currentValue;
    private double previousValue;
    private double valueChange;
    private double percentageChange;
    private double correlation;
    private double beta;
    private double trackingError;
    private double informationRatio;
    private List<Double> historicalValues;
    private TimeInterval interval;
    private Instant lastUpdated;
}
