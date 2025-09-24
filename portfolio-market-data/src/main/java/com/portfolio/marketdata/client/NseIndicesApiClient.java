package com.portfolio.marketdata.client;

import org.springframework.stereotype.Component;

import com.portfolio.marketdata.client.base.AbstractApiClient;
import com.portfolio.marketdata.config.MarketDataApiConfig;
import com.portfolio.marketdata.model.indices.IndexData;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Client for the NSE Indices API.
 */
@Slf4j
@Component
public class NseIndicesApiClient extends AbstractApiClient {

    /**
     * Creates a new NseIndicesApiClient with the specified configuration.
     * 
     * @param config the NSE indices API configuration
     */
    public NseIndicesApiClient(MarketDataApiConfig config) {
        super(config);
    }

    /**
     * Gets the index data for the specified index symbol.
     * 
     * @param indexSymbol the index symbol
     * @return a Mono of IndexData
     */
    public Mono<IndexData> getIndexData(String indexSymbol) {
        String path = config.getNseIndicesEndpoint() + "/" + indexSymbol;
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
