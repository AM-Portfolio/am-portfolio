package com.portfolio.model.analytics.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

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
     * Core identifiers for the analytics request
     */
    @Valid
    @lombok.Builder.Default
    private CoreIdentifiers coreIdentifiers = new CoreIdentifiers();
    
    /**
     * Pagination parameters for the results
     */
    @Valid
    private PaginationRequest pagination;
    
    /**
     * Feature toggles for controlling which analytics to include
     */
    @Valid
    @lombok.Builder.Default
    private FeatureToggles featureToggles = new FeatureToggles();
    
    /**
     * Configuration parameters for the requested features
     */
    @Valid
    @lombok.Builder.Default
    private FeatureConfiguration featureConfiguration = new FeatureConfiguration();
    
    /**
     * Core identifiers for the analytics request
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @SuperBuilder
    public static class CoreIdentifiers {
        /**
         * The portfolio ID to analyze
         */
        @NotBlank(message = "Portfolio ID is required")
        private String portfolioId;
        
        /**
         * The index symbol to analyze (e.g., "NIFTY 50", "NIFTY BANK")
         */
        @Pattern(regexp = "^[A-Za-z0-9 ]+$", message = "Index symbol must contain only alphanumeric characters and spaces")
        private String indexSymbol;
        
        /**
         * Optional comparison index symbol for relative performance analysis
         */
        @Pattern(regexp = "^[A-Za-z0-9 ]*$", message = "Comparison index symbol must contain only alphanumeric characters and spaces")
        private String comparisonIndexSymbol;
    }
    
    /**
     * Feature toggles for controlling which analytics to include
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @SuperBuilder
    public static class FeatureToggles {
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
    
    /**
     * Configuration parameters for the requested features
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @SuperBuilder
    public static class FeatureConfiguration {
        /**
         * Number of top movers to return (default will be applied if not specified)
         */
        @Min(value = 1, message = "Movers limit must be at least 1")
        private Integer moversLimit;
    }

}
