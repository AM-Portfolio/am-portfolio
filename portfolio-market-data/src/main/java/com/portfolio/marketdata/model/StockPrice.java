package com.portfolio.marketdata.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a stock price data point with symbol, price, and exchange information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockPrice {
    
    /**
     * The stock symbol
     */
    private String symbol;
    
    /**
     * The closing price of the stock
     */
    private Double close;
    
    /**
     * The exchange where the stock is traded
     */
    private String exchange;
}
