package com.portfolio.marketdata.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wrapper for historical market data responses, containing a map of symbols to their respective historical data,
 * along with metadata like processing time, date range, and cache status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HistoricalDataResponseWrapper {
    
    /**
     * Start date for the historical data
     */
    private LocalDate fromDate;
    
    /**
     * End date for the historical data
     */
    private LocalDate toDate;
    
    /**
     * Map of symbols to their respective historical data responses
     */
    private Map<String, HistoricalDataResponse> data;
    
    /**
     * Time interval for the data points (e.g., "day", "15min")
     */
    private String interval;
    
    /**
     * Total number of data points across all symbols
     */
    private int totalDataPoints;
    
    /**
     * List of symbols for which data was requested
     */
    private List<String> symbols;
    
    /**
     * Total number of symbols requested
     */
    private int totalSymbols;
    
    /**
     * Number of symbols for which data was successfully retrieved
     */
    private int successfulSymbols;
    
    /**
     * Flag indicating whether the data was retrieved from cache
     */
    private boolean cached;
    
    /**
     * Processing time in milliseconds for the entire request
     */
    private long processingTimeMs;
}
