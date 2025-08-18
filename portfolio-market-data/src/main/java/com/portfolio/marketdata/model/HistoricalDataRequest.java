package com.portfolio.marketdata.model;

import java.time.LocalDate;
import java.util.List;

import com.portfolio.model.market.TimeFrame;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request parameters for historical market data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricalDataRequest {
    
    /**
     * List of symbols to fetch historical data for
     */
    private List<String> symbols;
    
    /**
     * Start date for historical data (inclusive)
     */
    private LocalDate fromDate;
    
    /**
     * End date for historical data (inclusive)
     */
    private LocalDate toDate;
    
    /**
     * Time interval for data points (e.g., DAY, FIFTEEN_MIN)
     */
    private TimeFrame timeFrame;
    
    /**
     * Instrument type (e.g., EQ for equity)
     */
    private InstrumentType instrumentType;
    
    /**
     * Type of filtering to apply (ALL, START_END, CUSTOM)
     */
    private FilterType filterType;
    
    /**
     * Frequency for CUSTOM filtering (required when filterType is CUSTOM)
     */
    private Integer filterFrequency;
    
    /**
     * Whether to use continuous data
     */
    private Boolean continuous;
    
    /**
     * Whether to refresh the data or use cached data
     */
    @Builder.Default
    private Boolean refresh = false;
}
