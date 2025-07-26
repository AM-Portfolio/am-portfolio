package com.portfolio.marketdata.client;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.portfolio.marketdata.client.base.AbstractApiClient;
import com.portfolio.marketdata.config.MarketDataApiConfig;
import com.portfolio.marketdata.model.MarketDataResponseWrapper;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Client for the Market Data API.
 */
@Slf4j
@Component
public class MarketDataApiClient extends AbstractApiClient {

    private final MarketDataApiConfig config;

    /**
     * Creates a new MarketDataApiClient with the specified configuration.
     * 
     * @param config the market data API configuration
     */
    public MarketDataApiClient(MarketDataApiConfig config) {
        super(createWebClient(config));
        this.config = config;
    }
    
    /**
     * Creates a WebClient with the specified configuration.
     * 
     * @param config the market data API configuration
     * @return a WebClient
     */
    private static WebClient createWebClient(MarketDataApiConfig config) {
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
     * Gets the OHLC data for the specified symbols.
     * 
     * @param symbols the symbols to get OHLC data for
     * @return a Mono of MarketDataResponseWrapper
     */
    public Mono<MarketDataResponseWrapper> getOhlcData(List<String> symbols) {
        String symbolsParam = String.join(",", symbols);
        String path = config.getOhlcPath() + "?symbols=" + symbolsParam;
        log.debug("Fetching OHLC data for {} from {}", symbolsParam, path);
        
        // Now we can directly deserialize to MarketDataResponseWrapper since it extends HashMap
        return get(path, MarketDataResponseWrapper.class)
                .doOnSuccess(data -> log.debug("Successfully fetched OHLC data for {} with {} entries", symbolsParam, data.size()))
                .doOnError(e -> log.error("Failed to fetch OHLC data for {}: {}", symbolsParam, e.getMessage()));
    }

    /**
     * Gets the OHLC data for the specified symbols synchronously.
     * 
     * @param symbols the symbols to get OHLC data for
     * @return the MarketDataResponseWrapper
     */
    public MarketDataResponseWrapper getOhlcDataSync(List<String> symbols) {
        return getOhlcData(symbols).block();
    }
    
    /**
     * Gets the current prices for the specified symbols.
     * 
     * @param symbols the symbols to get current prices for
     * @return a map of symbol to current price
     */
    public Map<String, Double> getCurrentPrices(List<String> symbols) {
        MarketDataResponseWrapper wrapper = getOhlcDataSync(symbols);
        return wrapper.getData().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getLastPrice()
                ));
    }
}
