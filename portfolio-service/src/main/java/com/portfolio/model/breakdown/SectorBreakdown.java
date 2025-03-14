package com.portfolio.model.breakdown;

import lombok.Data;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.Map;
import java.util.List;

@Data
@Builder
public class SectorBreakdown {
    // Sector allocation
    private Map<String, BigDecimal> sectorAllocation;
    private Map<String, Double> sectorAllocationPercentage;
    
    // Sector performance
    private Map<String, BigDecimal> sectorPerformance;
    private Map<String, Double> sectorReturnPercentage;
    
    // Top performing sectors
    private List<SectorPerformance> topPerformingSectors;
    private List<SectorPerformance> underperformingSectors;
    
    // Sector diversification score (0-100)
    private int diversificationScore;
    
    @Data
    @Builder
    public static class SectorPerformance {
        private String sectorName;
        private BigDecimal totalValue;
        private BigDecimal gainLoss;
        private double returnPercentage;
        private int numberOfHoldings;
        private List<String> topHoldings;
    }
}
