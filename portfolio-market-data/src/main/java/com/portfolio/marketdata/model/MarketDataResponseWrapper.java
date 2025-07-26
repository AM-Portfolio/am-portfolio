package com.portfolio.marketdata.model;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Wrapper for market data responses, containing a map of symbols to their respective data.
 * Extends HashMap to directly deserialize the flat JSON structure where keys are symbols.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketDataResponseWrapper extends HashMap<String, MarketDataResponse> {
    
    /**
     * Gets the data as a map for backward compatibility.
     * 
     * @return this instance as a map
     */
    public Map<String, MarketDataResponse> getData() {
        return this;
    }
}
