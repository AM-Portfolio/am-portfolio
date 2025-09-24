package com.portfolio.marketdata.client.base;

import reactor.core.publisher.Mono;

/**
 * Base interface for all API clients.
 * Defines common methods that all API clients should implement.
 */
public interface ApiClient {
    
    /**
     * Gets the base URL for this API client.
     * 
     * @return the base URL
     */
    String getBaseUrl();
    
    /**
     * Gets the connection timeout in milliseconds.
     * 
     * @return connection timeout in milliseconds
     */
    int getConnectionTimeout();
    
    /**
     * Gets the read timeout in milliseconds.
     * 
     * @return read timeout in milliseconds
     */
    int getReadTimeout();
    
    /**
     * Gets the maximum number of retry attempts.
     * 
     * @return maximum retry attempts
     */
    int getMaxRetryAttempts();
    
    /**
     * Makes a GET request to the specified path with optional query parameters.
     * 
     * @param <T> the response type
     * @param path the API path
     * @param responseType the class of the response type
     * @param queryParams optional query parameters as key-value pairs
     * @return a Mono of the response type
     */
    <T> Mono<T> get(String path, Class<T> responseType, Object... queryParams);
    
    /**
     * Makes a POST request to the specified path with a request body and optional query parameters.
     * 
     * @param <T> the response type
     * @param <R> the request body type
     * @param path the API path
     * @param requestBody the request body
     * @param responseType the class of the response type
     * @param queryParams optional query parameters as key-value pairs
     * @return a Mono of the response type
     */
    <T, R> Mono<T> post(String path, R requestBody, Class<T> responseType, Object... queryParams);
}
