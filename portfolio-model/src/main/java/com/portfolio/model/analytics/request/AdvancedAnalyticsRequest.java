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
    private CoreIdentifiers coreIdentifiers;
    
    private PaginationRequest pagination;
    
    private FeatureToggles featureToggles;

    private FeatureConfiguration featureConfiguration;

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
