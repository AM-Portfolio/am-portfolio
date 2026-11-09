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
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class AdvancedAnalyticsRequest extends TimeFrameRequest {
    @lombok.Builder.Default
    private CoreIdentifiers coreIdentifiers = new CoreIdentifiers();
    
    @lombok.Builder.Default
    private PaginationRequest pagination = new PaginationRequest();
    
    @lombok.Builder.Default
    private FeatureToggles featureToggles = new FeatureToggles();

    @lombok.Builder.Default
    private FeatureConfiguration featureConfiguration = new FeatureConfiguration();

    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient java.util.Map<String, com.portfolio.model.market.MarketData> prefetchedMarketData;

    /**
     * Live OHLC/ticks for day movers and summary today%. Separate from period hist prefetch.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient java.util.Map<String, com.portfolio.model.market.MarketData> prefetchedLiveMarketData;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient java.util.Map<String, com.am.common.amcommondata.model.security.SecurityModel> prefetchedSecurityDetails;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient com.am.common.amcommondata.model.PortfolioModelV1 prefetchedPortfolio;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient boolean prefetchAttempted = false;

    public AdvancedAnalyticsRequest() {
        this.coreIdentifiers = new CoreIdentifiers();
        this.pagination = new PaginationRequest();
        this.featureToggles = new FeatureToggles();
        this.featureConfiguration = new FeatureConfiguration();
    }

    /**
     * Period analytics when a timeframe is set. Dates may be null; callers resolve via
     * {@code HistoricalDataRequestFactory.resolveWindow}.
     */
    public TimeFrameRequest getTimeFrameRequest() {
        if (this.getTimeFrame() == null) {
            return null;
        }
        return TimeFrameRequest.builder()
                .fromDate(this.getFromDate())
                .toDate(this.getToDate())
                .timeFrame(this.getTimeFrame())
                .build();
    }
}
