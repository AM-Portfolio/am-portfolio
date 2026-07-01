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
    @lombok.Builder.Default
    private CoreIdentifiers coreIdentifiers = new CoreIdentifiers();
    
    @lombok.Builder.Default
    private PaginationRequest pagination = new PaginationRequest();
    
    @lombok.Builder.Default
    private FeatureToggles featureToggles = new FeatureToggles();

    @lombok.Builder.Default
    private FeatureConfiguration featureConfiguration = new FeatureConfiguration();

    public TimeFrameRequest getTimeFrameRequest() {
        if (this.getFromDate() == null || this.getToDate() == null || this.getTimeFrame () == null) {
            return null;
        }
        return TimeFrameRequest.builder()
                .fromDate(this.getFromDate())
                .toDate(this.getToDate())
                .timeFrame(this.getTimeFrame())
                .build();
    }
}
