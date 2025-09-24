package com.portfolio.model.analytics.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
     * Feature toggles for controlling which analytics to include
     */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public  class FeatureToggles {
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
}
