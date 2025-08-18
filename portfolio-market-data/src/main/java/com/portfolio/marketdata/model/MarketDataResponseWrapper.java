package com.portfolio.marketdata.model;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wrapper for market data responses, containing a map of symbols to their respective data,
 * along with metadata like cache status and timestamp.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketDataResponseWrapper {
    
    /**
     * Map of symbols to their respective market data responses
     */
    private Map<String, MarketDataResponse> data;
    
    /**
     * Flag indicating whether the data was retrieved from cache
     */
    private boolean cached;
    
    /**
     * Timestamp of when the data was retrieved or generated
     */
    private long timestamp;
}
