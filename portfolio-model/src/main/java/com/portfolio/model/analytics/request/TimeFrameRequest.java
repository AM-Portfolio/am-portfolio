package com.portfolio.model.analytics.request;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

import com.portfolio.model.market.TimeFrame;

/**
 * Common request parameters for time frame-based analytics
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TimeFrameRequest {

    /**
     * Start date for the analysis period
     */
    private LocalDate fromDate;
    
    /**
     * End date for the analysis period (defaults to current date if not specified)
     */
    private LocalDate toDate;
    
    /**
     * Time frame interval for data points (e.g., DAY, HOUR, etc.)
     */
    @Builder.Default
    private TimeFrame timeFrame = null;
}
