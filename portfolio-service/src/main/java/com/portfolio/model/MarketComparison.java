package com.portfolio.model;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;

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
