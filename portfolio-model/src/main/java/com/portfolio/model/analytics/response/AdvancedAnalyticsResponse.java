package com.portfolio.model.analytics.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.model.analytics.GainerLoser;
import com.portfolio.model.analytics.Heatmap;
import com.portfolio.model.analytics.MarketCapAllocation;
import com.portfolio.model.analytics.SectorAllocation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Response object for advanced analytics that combines multiple analytics features
 * with timeframe support.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdvancedAnalyticsResponse {

    private String portfolioId;
    /**
     * The index symbol that was analyzed
     */
    private String indexSymbol;
    
    /**
     * The comparison index symbol if provided in the request
     */
    private String comparisonIndexSymbol;
    
    /**
     * Start date of the analysis period
     */
    private LocalDate startDate;
    
    /**
     * End date of the analysis period
     */
    private LocalDate endDate;
    
    /**
     * Timestamp when the analysis was generated
     */
    private Instant timestamp;
    
    /**
     * Sector heatmap data if requested
     */
    private Heatmap heatmap;
    
    /**
     * Top gainers and losers if requested
     */
    private GainerLoser movers;
    
    /**
     * Sector allocation data if requested
     */
    private SectorAllocation sectorAllocation;
    
    /**
     * Market cap allocation data if requested
     */
    private MarketCapAllocation marketCapAllocation;
    
    /**
     * Performance metrics over the specified time period
     */
    private PerformanceMetrics performanceMetrics;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceMetrics {
        /**
         * Overall index performance over the period
         */
        private double indexPerformancePercent;
        
        /**
         * Comparison index performance over the period (if comparison index was provided)
         */
        private Double comparisonIndexPerformancePercent;
        
        /**
         * Relative performance between the main index and comparison index
         */
        private Double relativePerformancePercent;
        
        /**
         * Volatility measure over the period
         */
        private double volatility;
        
        /**
         * Maximum drawdown over the period
         */
        private double maxDrawdownPercent;
        
        /**
         * Best performing sector over the period
         */
        private String bestPerformingSector;
        
        /**
         * Worst performing sector over the period
         */
        private String worstPerformingSector;
    }
}
