package com.portfolio.marketdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import lombok.Data;

/**
 * Configuration properties for the NSE Indices API.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "nse-indices.api")
@Primary
public class NseIndicesApiConfig {
    /**
     * Base URL for the NSE indices API.
     */
    private String baseUrl = "http://am-nse-indices.dev.svc.cluster.local:8080";
    
    /**
     * API path for NSE indices data.
     */
    private String indicesPath = "/api/v1/nse-indices";
    
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
