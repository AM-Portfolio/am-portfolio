package com.portfolio.marketdata.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.portfolio.marketdata.client.base.AbstractApiClient;
import com.portfolio.marketdata.config.NseIndicesApiConfig;
import com.portfolio.marketdata.model.indices.IndexData;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Client for the NSE Indices API.
 */
@Slf4j
@Component
public class NseIndicesApiClient extends AbstractApiClient {

    private final NseIndicesApiConfig config;

    /**
     * Creates a new NseIndicesApiClient with the specified configuration.
     * 
     * @param config the NSE indices API configuration
     */
    public NseIndicesApiClient(NseIndicesApiConfig config) {
        super(createWebClient(config));
        this.config = config;
    }

    /**
     * Creates a WebClient with the specified configuration.
     * 
     * @param config the NSE indices API configuration
     * @return a WebClient
     */
    private static WebClient createWebClient(NseIndicesApiConfig config) {
        return WebClient.builder()
                .baseUrl(config.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                .build();
    }
    
    /**
     * Gets the WebClient instance. Protected for testing purposes.
     * 
     * @return the WebClient instance
     */
    protected WebClient getWebClient() {
        return webClient;
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
     * Gets the index data for the specified index symbol.
     * 
     * @param indexSymbol the index symbol
     * @return a Mono of IndexData
     */
    public Mono<IndexData> getIndexData(String indexSymbol) {
        String path = config.getIndicesPath() + "/" + indexSymbol;
        log.debug("Fetching index data for {} from {}", indexSymbol, path);
        
        return get(path, IndexData.class)
                .doOnSuccess(data -> log.debug("Successfully fetched index data for {}", indexSymbol))
                .doOnError(e -> log.error("Failed to fetch index data for {}: {}", indexSymbol, e.getMessage()));
    }
    
    /**
     * Gets the index data for the specified index symbol synchronously.
     * 
     * @param indexSymbol the index symbol
     * @return the IndexData
     */
    public IndexData getIndexDataSync(String indexSymbol) {
        return getIndexData(indexSymbol).block();
    }
}
