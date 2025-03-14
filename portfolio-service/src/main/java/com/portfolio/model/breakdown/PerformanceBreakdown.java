package com.portfolio.model.breakdown;

import com.portfolio.model.StockPerformanceGroup;
import lombok.Data;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class PerformanceBreakdown {
    private List<StockPerformanceGroup> topGainers;
    private List<StockPerformanceGroup> topLosers;
    private List<StockPerformanceGroup> mostVolatile;
    private List<StockPerformanceGroup> highestVolume;
    
    private BigDecimal totalGainAmount;
    private BigDecimal totalLossAmount;
    private double averageGainPercentage;
    private double averageLossPercentage;
    
    // Performance metrics by time window
    private List<TimeWindowPerformance> dailyPerformance;
    private List<TimeWindowPerformance> weeklyPerformance;
    private List<TimeWindowPerformance> monthlyPerformance;
    
    @Data
    @Builder
    public static class TimeWindowPerformance {
        private String timeWindow;
        private BigDecimal returnAmount;
        private double returnPercentage;
        private int profitableDays;
        private int unprofitableDays;
    }
}
