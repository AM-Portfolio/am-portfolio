package com.portfolio.marketdata.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response wrapper for live market data prices, containing a list of stock prices
 * along with metadata like processing time, cache status and count.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketDataLivePricesResponse {
    
    /**
     * Processing time in milliseconds
     */
    private long processingTimeMs;
    
    /**
     * Flag indicating whether the data was retrieved from cache
     */
    private boolean cached;
    
    /**
     * Number of price entries in the response
     */
    private int count;
    
    /**
     * List of stock prices
     */
    private List<StockPrice> prices;
}
