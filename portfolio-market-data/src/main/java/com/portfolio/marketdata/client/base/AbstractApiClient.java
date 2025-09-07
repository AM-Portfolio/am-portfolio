package com.portfolio.marketdata.client.base;

import java.time.Duration;
import java.util.function.Function;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.marketdata.config.MarketDataApiConfig;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Abstract implementation of ApiClient that provides common functionality.
 */
@Slf4j
public abstract class AbstractApiClient implements ApiClient {

    protected final WebClient webClient;
    protected final MarketDataApiConfig config;
    
    /**
     * Creates a new AbstractApiClient with the specified WebClient and config.
     * 
     * @param webClient the WebClient to use
     * @param config the API configuration
     */
    protected AbstractApiClient(WebClient webClient, MarketDataApiConfig config) {
        this.webClient = webClient;
        this.config = config;
    }
    
    /**
     * Creates a new AbstractApiClient with the specified configuration.
     * 
     * @param config the API configuration
     */
    protected AbstractApiClient(MarketDataApiConfig config) {
        this.config = config;
        this.webClient = createDefaultWebClient(config.getBaseUrl());
    }
    
    /**
     * Creates a default WebClient with common configuration.
     * 
     * @param baseUrl the base URL for the API
     * @return a configured WebClient
     */
    protected static WebClient createDefaultWebClient(String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                .build();
    }

    @Override
    public <T> Mono<T> get(String path, Class<T> responseType) {
        String requestId = generateRequestId();
        String fullUrl = config.getBaseUrl() + path;
        log.info("API Request [{}] - GET - Path: {} - ResponseType: {}", requestId, path, responseType.getSimpleName());
        
        // Log curl command for debugging
        log.debug("API Request [{}] - curl command: curl -X GET '{}' -H 'Content-Type: application/json'", 
                requestId, fullUrl);
        
        return executeWithRetry(client -> client
                .get()
                .uri(path)
                .retrieve()
                .bodyToMono(responseType), requestId);
    }

    @Override
    public <T> Mono<T> get(String path, Class<T> responseType, Object... queryParams) {
        String requestId = generateRequestId();
        
        // Build query params string for logging
        StringBuilder paramsLog = new StringBuilder();
        if (queryParams.length > 0) {
            for (int i = 0; i < queryParams.length; i += 2) {
                if (i > 0) paramsLog.append(", ");
                if (i + 1 < queryParams.length) {
                    paramsLog.append(queryParams[i]).append("=").append(queryParams[i + 1]);
                }
            }
        }
        
        log.info("API Request [{}] - GET - Path: {} - Params: {} - ResponseType: {}", 
                requestId, path, paramsLog.toString(), responseType.getSimpleName());
        
        // Build the full URL for curl command
        StringBuilder curlUrlBuilder = new StringBuilder(config.getBaseUrl());
        if (!path.startsWith("/")) {
            curlUrlBuilder.append("/");
        }
        curlUrlBuilder.append(path);
        if (path.contains("?")) {
            curlUrlBuilder.append("&");
        } else {
            curlUrlBuilder.append("?");
        }
        
        // Add query parameters to curl URL
        StringBuilder curlParamsBuilder = new StringBuilder();
        for (int i = 0; i < queryParams.length; i += 2) {
            if (i > 0) curlParamsBuilder.append("&");
            if (i + 1 < queryParams.length) {
                Object value = queryParams[i + 1];
                String valueStr = value != null ? value.toString() : "";
                curlParamsBuilder.append(queryParams[i]).append("=").append(valueStr);
            }
        }
        
        // Log curl command for debugging
        log.debug("API Request [{}] - curl command: curl -X GET '{}{}' -H 'Content-Type: application/json'", 
                requestId, curlUrlBuilder.toString(), curlParamsBuilder.toString());
        
        return executeWithRetry(client -> client
                .get()
                .uri(uriBuilder -> {
                    if (queryParams.length % 2 != 0) {
                        throw new IllegalArgumentException("Query parameters must be provided as key-value pairs");
                    }
                    
                    for (int i = 0; i < queryParams.length; i += 2) {
                        uriBuilder = uriBuilder.queryParam(queryParams[i].toString(), queryParams[i + 1]);
                    }
                    
                    return uriBuilder.path(path).build();
                })
                .retrieve()
                .bodyToMono(responseType), requestId);
    }
    
    @Override
    public <T, R> Mono<T> post(String path, R requestBody, Class<T> responseType) {
        String requestId = generateRequestId();
        String requestBodyStr = serializeRequestBody(requestBody);
        
        log.info("API Request [{}] - POST - Path: {} - RequestBody: {} - ResponseType: {}", 
                requestId, path, requestBodyStr, responseType.getSimpleName());
        
        // Log curl command for debugging
        String fullUrl = config.getBaseUrl() + path;
        log.debug("API Request [{}] - curl command: curl -X POST '{}' -H 'Content-Type: application/json' -d '{}'", 
                requestId, fullUrl, requestBodyStr);
        
        return executeWithRetry(client -> client
                .post()
                .uri(path)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(responseType), requestId);
    }
    
    @Override
    public <T, R> Mono<T> post(String path, R requestBody, Class<T> responseType, Object... queryParams) {
        String requestId = generateRequestId();
        
        // Build query params string for logging
        StringBuilder paramsLog = new StringBuilder();
        if (queryParams.length > 0) {
            for (int i = 0; i < queryParams.length; i += 2) {
                if (i > 0) paramsLog.append(", ");
                if (i + 1 < queryParams.length) {
                    paramsLog.append(queryParams[i]).append("=").append(queryParams[i + 1]);
                }
            }
        }
        
        String requestBodyStr = serializeRequestBody(requestBody);
        
        log.info("API Request [{}] - POST - Path: {} - Params: {} - RequestBody: {} - ResponseType: {}", 
                requestId, path, paramsLog.toString(), requestBodyStr, responseType.getSimpleName());
        
        // Build the full URL for curl command
        StringBuilder curlUrlBuilder = new StringBuilder(config.getBaseUrl());
        if (!path.startsWith("/")) {
            curlUrlBuilder.append("/");
        }
        curlUrlBuilder.append(path);
        if (path.contains("?")) {
            curlUrlBuilder.append("&");
        } else {
            curlUrlBuilder.append("?");
        }
        
        // Add query parameters to curl URL
        StringBuilder curlParamsBuilder = new StringBuilder();
        for (int i = 0; i < queryParams.length; i += 2) {
            if (i > 0) curlParamsBuilder.append("&");
            if (i + 1 < queryParams.length) {
                Object value = queryParams[i + 1];
                String valueStr = value != null ? value.toString() : "";
                curlParamsBuilder.append(queryParams[i]).append("=").append(valueStr);
            }
        }
        
        // Log curl command for debugging
        log.debug("API Request [{}] - curl command: curl -X POST '{}{}' -H 'Content-Type: application/json' -d '{}'", 
                requestId, curlUrlBuilder.toString(), curlParamsBuilder.toString(), requestBodyStr);
        
        return executeWithRetry(client -> client
                .post()
                .uri(uriBuilder -> {
                    if (queryParams.length % 2 != 0) {
                        throw new IllegalArgumentException("Query parameters must be provided as key-value pairs");
                    }
                    
                    for (int i = 0; i < queryParams.length; i += 2) {
                        uriBuilder = uriBuilder.queryParam(queryParams[i].toString(), queryParams[i + 1]);
                    }
                    
                    return uriBuilder.path(path).build();
                })
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(responseType), requestId);
    }
    
    @Override
    public String getBaseUrl() {
        return config.getBaseUrl();
    }
    
    @Override
    public int getConnectionTimeout() {
        return config.getConnectionTimeout();
    }
    
    @Override
    public int getReadTimeout() {
        return config.getReadTimeout();
    }
    
    @Override
    public int getMaxRetryAttempts() {
        return config.getMaxRetryAttempts();
    }
    
    /**
     * Executes a WebClient request with retry logic and comprehensive logging.
     * 
     * @param <T> the response type
     * @param requestFunction the function that executes the request
     * @param requestId the unique identifier for this request
     * @return a Mono of the response type
     */
    protected <T> Mono<T> executeWithRetry(Function<WebClient, Mono<T>> requestFunction, String requestId) {
        long startTime = System.currentTimeMillis();
        
        return requestFunction.apply(webClient)
                .timeout(Duration.ofMillis(getReadTimeout()))
                .doOnSubscribe(s -> log.debug("API Request [{}] - Executing", requestId))
                .doOnSuccess(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("API Request [{}] - Completed successfully in {}ms - Status: OK", requestId, duration);
                })
                .doOnError(e -> {
                    long duration = System.currentTimeMillis() - startTime;
                    String statusCode = "";
                    if (e instanceof WebClientResponseException) {
                        WebClientResponseException wcre = (WebClientResponseException) e;
                        statusCode = String.valueOf(wcre.getStatusCode().value());
                        log.error("API Request [{}] - Failed after {}ms - Status: {} - Error: {}", 
                                requestId, duration, statusCode, wcre.getMessage());
                    } else {
                        log.error("API Request [{}] - Failed after {}ms - Error: {}", 
                                requestId, duration, e.getMessage());
                    }
                })
                .retryWhen(Retry.backoff(getMaxRetryAttempts(), Duration.ofSeconds(1))
                        .filter(e -> !(e instanceof WebClientResponseException.NotFound))
                        .doBeforeRetry(retrySignal -> {
                            log.warn("API Request [{}] - Retry attempt {} of {} after failure: {}", 
                                    requestId, retrySignal.totalRetries() + 1, getMaxRetryAttempts(), 
                                    retrySignal.failure().getMessage());
                        })
                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                            log.error("API Request [{}] - Retry attempts exhausted: {}", 
                                    requestId, retrySignal.failure().getMessage());
                            return retrySignal.failure();
                        }));
    }
    
    /**
     * Executes a WebClient request with retry logic and comprehensive logging.
     * This overload generates a new request ID.
     * 
     * @param <T> the response type
     * @param requestFunction the function that executes the request
     * @return a Mono of the response type
     */
    protected <T> Mono<T> executeWithRetry(Function<WebClient, Mono<T>> requestFunction) {
        return executeWithRetry(requestFunction, generateRequestId());
    }
    
    /**
     * Generates a unique request ID for logging purposes.
     * 
     * @return a unique request ID
     */
    private String generateRequestId() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Serializes a request body object to a string for logging purposes.
     * Limits the output to prevent excessive logging.
     * 
     * @param <R> the request body type
     * @param requestBody the request body object
     * @return a string representation of the request body
     */
    private <R> String serializeRequestBody(R requestBody) {
        if (requestBody == null) {
            return "null";
        }
        
        try {
            // Use Jackson for serialization
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(requestBody);
            
            // Limit the length for logging
            int maxLength = 500;
            if (json.length() > maxLength) {
                return json.substring(0, maxLength) + "... [truncated]";
            }
            return json;
        } catch (JsonProcessingException e) {
            // Fallback to toString() if serialization fails
            String result = requestBody.toString();
            int maxLength = 100;
            if (result.length() > maxLength) {
                return result.substring(0, maxLength) + "... [truncated]";
            }
            return result;
        }
    }
}
