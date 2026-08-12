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
     * API path for historical charts data.
     */
    private String historicalChartsEndpoint = "/v1/analysis/historical-charts";

    /**
     * API path for securities data.
     */
    private String securitiesEndpoint;

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

    /**
     * Batch fetch configuration.
     */
    private BatchConfig batch = new BatchConfig();

    @Data
    public static class BatchConfig {
        /**
         * Maximum symbols per single API batch call.
         * Default: 20 (parallel batching for fast concurrent queries).
         */
        private int chunkSize = 20;

        /**
         * When true: splits symbols into parallel CHUNK_SIZE batches.
         * When false: sends all symbols in one single batch.
         * Default: true (parallel batching).
         */
        private boolean parallelBatchingEnabled = true;

        /**
         * Max OHLC/historical chunks in flight at once.
         * Keeps Upstox fan-out bounded when a portfolio has 100+ holdings.
         */
        private int maxParallelChunks = 2;
    }
}
