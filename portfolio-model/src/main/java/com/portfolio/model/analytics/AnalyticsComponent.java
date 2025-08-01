package com.portfolio.model.analytics;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Encapsulates all analytics components in a single model
 * Used to group related analytics data together
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnalyticsComponent {
    
    /**
     * Heatmap visualization data
     */
    private Heatmap heatmap;
    
    /**
     * Top gainers and losers
     */
    private GainerLoser movers;
    
    /**
     * Sector-wise allocation breakdown
     */
    private SectorAllocation sectorAllocation;
    
    /**
     * Market capitalization allocation breakdown
     */
    private MarketCapAllocation marketCapAllocation;
}
