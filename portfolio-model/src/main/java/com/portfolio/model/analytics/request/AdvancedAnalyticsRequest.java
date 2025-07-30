package com.portfolio.model.analytics.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Request object for advanced analytics that combines multiple analytics features
 * with timeframe support.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AdvancedAnalyticsRequest extends TimeFrameRequest {

    /**
     * 
     * The portfolio ID to analyze
     */
    private String portfolioId;
    
    /**
     * 
     * The index symbol to analyze (e.g., "NIFTY 50", "NIFTY BANK")
     */
    private String indexSymbol;
    
    /**
     * Pagination parameters for the results
     */
    private PaginationRequest pagination;
    
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
