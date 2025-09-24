package com.portfolio.marketdata.model;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response model for historical data for a specific symbol.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HistoricalDataResponse {
    
    /**
     * The symbol for which historical data is provided
     */
    private String symbol;
    
    /**
     * Start date for the historical data
     */
    private LocalDate fromDate;
    
    /**
     * End date for the historical data
     */
    private LocalDate toDate;
    
    /**
     * The time interval for the data points (e.g., "day", "15min")
     */
    private String interval;
    
    /**
     * The actual historical data
     */
    private HistoricalData data;
    
    /**
     * Number of data points after filtering
     */
    private int count;
    
    /**
     * Original number of data points before filtering
     */
    private int originalCount;
    
    /**
     * Flag indicating whether the data was filtered
     */
    private boolean filtered;
    
    /**
     * The type of filtering applied (e.g., "ALL", "START_END", "CUSTOM")
     */
    private String filterType;
    
    /**
     * Processing time in milliseconds for this symbol
     */
    private long processingTimeMs;
}
