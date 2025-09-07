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
    private String baseUrl;
    
    /**
     * API path for OHLC data.
     */
    private String ohlcEndpoint;
    
    /**
     * API path for historical data.
     */
    private String historicalDataEndpoint;

    /**
     * API path for NSE indices data.
     */
    private String nseIndicesEndpoint;
    
    /**
     * Connection timeout in milliseconds.
     */
    private int connectionTimeout = 10000;
    
    /**
     * Read timeout in milliseconds.
     */
    private int readTimeout = 10000;
    
    /**
     * Maximum number of retry attempts.
     */
    private int maxRetryAttempts = 2;
}
