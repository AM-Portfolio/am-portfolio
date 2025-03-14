package com.portfolio.model.performance;

import lombok.Data;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class ProfitabilityMetrics {
    // Basic counts
    private int profitableDays;      // Days with positive returns
    private int unprofitableDays;    // Days with negative returns
    private int breakEvenDays;       // Days with zero/minimal change
    
    // Streak tracking
    private int currentProfitableStreak;    // Current consecutive profitable days
    private int longestProfitableStreak;    // Longest streak of profitable days
    private int currentUnprofitableStreak;  // Current consecutive unprofitable days
    private int longestUnprofitableStreak;  // Longest streak of unprofitable days
    
    // Profit/Loss magnitudes
    private BigDecimal averageProfitAmount;     // Average gain on profitable days
    private BigDecimal averageLossAmount;       // Average loss on unprofitable days
    private double averageProfitPercentage;     // Average percentage gain
    private double averageLossPercentage;       // Average percentage loss
    
    // Best/Worst performance
    private DailyPerformance bestDay;           // Day with highest return
    private DailyPerformance worstDay;          // Day with lowest return
    
    // Win rate calculations
    private double winRate;                     // profitableDays / totalTradingDays
    private double profitFactor;                // totalProfit / totalLoss ratio
    
    @Data
    @Builder
    public static class DailyPerformance {
        private LocalDate date;
        private BigDecimal returnAmount;
        private double returnPercentage;
        private List<String> contributingFactors;  // What caused this performance
    }
    
    // Utility methods
    public int getTotalTradingDays() {
        return profitableDays + unprofitableDays + breakEvenDays;
    }
    
    public double getRiskRewardRatio() {
        return Math.abs(averageProfitAmount.doubleValue() / averageLossAmount.doubleValue());
    }
    
    public boolean isCurrentlyProfitable() {
        return currentProfitableStreak > 0;
    }
}
