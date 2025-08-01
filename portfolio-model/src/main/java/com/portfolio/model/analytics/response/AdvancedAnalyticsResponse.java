package com.portfolio.model.analytics.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.model.analytics.AnalyticsComponent;

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
    private String indexSymbol;
    
    private String comparisonIndexSymbol;
    
    private LocalDate startDate;
    
    private LocalDate endDate;
    
    private Instant timestamp;
    
    private AnalyticsComponent analytics;
    
    private PerformanceMetrics performanceMetrics;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceMetrics {
        private double indexPerformancePercent;
        
        private Double comparisonIndexPerformancePercent;
        
        private Double relativePerformancePercent;
        
        private double volatility;
        
        private double maxDrawdownPercent;
        
        private String bestPerformingSector;
        
        private String worstPerformingSector;
    }
}
