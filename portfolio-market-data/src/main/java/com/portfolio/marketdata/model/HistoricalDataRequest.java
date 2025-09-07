package com.portfolio.marketdata.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request parameters for historical market data.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoricalDataRequest {
    
    /**
     * Symbols to fetch historical data for, as a comma-separated string
     */
    private String symbols;
    
    /**
     * List of symbols to fetch historical data for
     * This field is used for backward compatibility and is not serialized
     */
    @JsonIgnore
    private List<String> symbolsList;
    
    /**
     * Start date for historical data (inclusive)
     */
    @JsonProperty("from")
    private LocalDate fromDate;
    
    /**
     * End date for historical data (inclusive)
     */
    @JsonProperty("to")
    private LocalDate toDate;
    
    /**
     * Time interval for data points (e.g., DAY, FIFTEEN_MIN)
     */
    @JsonProperty("interval")
    private String timeFrame;
    
    /**
     * Instrument type (e.g., EQ for equity)
     */
    private String instrumentType;
    
    /**
     * Type of filtering to apply (ALL, START_END, CUSTOM)
     */
    private String filterType;
    
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
    private Boolean forceRefresh = false;
    
    /**
     * Additional parameters for the request
     */
    private Map<String, String> additionalParams;
    
}
