package com.portfolio.marketdata.client;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
     * @param refresh whether to refresh the data or use cached data (default: true)
     * @return a Mono of MarketDataResponseWrapper
     */
    public Mono<MarketDataResponseWrapper> getOhlcData(List<String> symbols, boolean refresh) {
        // URL encode each symbol individually to handle special characters like &
        String symbolsParam = symbols.stream()
                .map(symbol -> URLEncoder.encode(symbol, StandardCharsets.UTF_8))
                .collect(Collectors.joining(","));
                
        String path = config.getOhlcPath() + "?symbols=" + symbolsParam + "&refresh=" + refresh;
        log.debug("Fetching OHLC data for {} from {} with refresh={}", String.join(",", symbols), path, refresh);
        
        // Deserialize to MarketDataResponseWrapper
        return get(path, MarketDataResponseWrapper.class)
                .doOnSuccess(data -> log.debug("Successfully fetched OHLC data for {} with {} entries", String.join(",", symbols), 
                        data.getData() != null ? data.getData().size() : 0))
                .doOnError(e -> log.error("Failed to fetch OHLC data for {}: {}", String.join(",", symbols), e.getMessage()));
    }
    
    /**
     * Gets the OHLC data for the specified symbols with default refresh=true.
     * 
     * @param symbols the symbols to get OHLC data for
     * @return a Mono of MarketDataResponseWrapper
     */
    public Mono<MarketDataResponseWrapper> getOhlcData(List<String> symbols) {
        return getOhlcData(symbols, true);
    }

    /**
     * Gets the OHLC data for the specified symbols synchronously.
     * 
     * @param symbols the symbols to get OHLC data for
     * @param refresh whether to refresh the data or use cached data (default: true)
     * @return the MarketDataResponseWrapper
     */
    public MarketDataResponseWrapper getOhlcDataSync(List<String> symbols, boolean refresh) {
        return getOhlcData(symbols, refresh).block();
    }
    
    /**
     * Gets the OHLC data for the specified symbols synchronously with default refresh=true.
     * 
     * @param symbols the symbols to get OHLC data for
     * @return the MarketDataResponseWrapper
     */
    public MarketDataResponseWrapper getOhlcDataSync(List<String> symbols) {
        return getOhlcDataSync(symbols, true);
    }
    
    /**
     * Gets the current prices for the specified symbols.
     * 
     * @param symbols the symbols to get current prices for
     * @return a map of symbol to current price
     */
    public Map<String, Double> getCurrentPrices(List<String> symbols) {
        MarketDataResponseWrapper wrapper = getOhlcDataSync(symbols);
        if (wrapper.getData() == null) {
            return Map.of();
        }
        return wrapper.getData().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getLastPrice()
                ));
    }
}
