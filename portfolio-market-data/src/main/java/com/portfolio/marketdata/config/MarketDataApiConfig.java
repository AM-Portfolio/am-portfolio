package com.portfolio.marketdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Configuration properties for the Market Data API.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "market-data.api")
public class MarketDataApiConfig {
    /**
     * Base URL for the market data API.
     */
    private String baseUrl = "http://localhost:8092";
    
    /**
     * API path for OHLC data.
     */
    private String ohlcPath = "/api/v1/market-data/ohlc";

    private String livePricesPath = "/api/v1/market-data/live-prices";
    
    /**
     * API path for historical data.
     */
    private String historicalDataPath = "/api/v1/market-data/historical-data";
    
    /**
     * Connection timeout in milliseconds.
     */
    private int connectionTimeout = 5000;
    
    /**
     * Read timeout in milliseconds.
     */
    private int readTimeout = 5000;
    
    /**
     * Maximum number of retry attempts.
     */
    private int maxRetryAttempts = 3;
}
