package com.portfolio.model.analytics.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request object for advanced analytics that combines multiple analytics features
 * with timeframe support.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvancedAnalyticsRequest {
    /**
     * The index symbol to analyze (e.g., "NIFTY 50", "NIFTY BANK")
     */
    private String indexSymbol;
    
    /**
     * Start date for the analysis period
     */
    private LocalDate startDate;
    
    /**
     * End date for the analysis period (defaults to current date if not specified)
     */
    private LocalDate endDate;
    
    /**
     * Whether to include heatmap data in the response
     */
    private boolean includeHeatmap;
    
    /**
     * Whether to include top movers (gainers/losers) in the response
     */
    private boolean includeMovers;
    
    /**
     * Whether to include sector allocation data in the response
     */
    private boolean includeSectorAllocation;
    
    /**
     * Whether to include market cap allocation data in the response
     */
    private boolean includeMarketCapAllocation;
    
    /**
     * Number of top movers to return (default will be applied if not specified)
     */
    private Integer moversLimit;
    
    /**
     * Optional comparison index symbol for relative performance analysis
     */
    private String comparisonIndexSymbol;
}
