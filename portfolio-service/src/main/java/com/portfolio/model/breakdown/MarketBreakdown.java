package com.portfolio.model.breakdown;

import lombok.Data;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.Map;
import java.util.List;

@Data
@Builder
public class MarketBreakdown {
    // Market cap breakdown
    private Map<MarketCapType, BigDecimal> marketCapAllocation;
    private Map<MarketCapType, Double> marketCapPercentage;
    
    // Geographic breakdown
    private Map<String, BigDecimal> geographicAllocation;
    private Map<String, Double> geographicPercentage;
    
    // Exchange breakdown
    private Map<String, BigDecimal> exchangeAllocation;
    private Map<String, Double> exchangePercentage;
    
    // Market type performance
    private List<MarketTypePerformance> marketTypePerformance;
    
    @Data
    @Builder
    public static class MarketTypePerformance {
        private MarketCapType marketType;
        private BigDecimal totalValue;
        private BigDecimal gainLoss;
        private double returnPercentage;
        private int numberOfHoldings;
        private List<String> topHoldings;
    }
    
    public enum MarketCapType {
        LARGE_CAP,
        MID_CAP,
        SMALL_CAP,
        MICRO_CAP,
        NANO_CAP
    }
}
