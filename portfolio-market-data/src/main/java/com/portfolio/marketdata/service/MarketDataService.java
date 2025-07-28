package com.portfolio.marketdata.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Service;

import com.portfolio.marketdata.client.MarketDataApiClient;
import com.portfolio.marketdata.model.MarketDataResponse;
import com.portfolio.marketdata.model.MarketDataResponseWrapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.scheduler.Schedulers;

/**
 * Service for fetching and processing market data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataService {

    private final MarketDataApiClient marketDataApiClient;
    
    /**
     * Get OHLC data for the specified symbols.
     * 
     * @param symbols List of symbols to fetch data for
     * @param refresh Whether to refresh the data or use cached data
     * @return Map of symbols to their respective market data
     */
    public Map<String, MarketDataResponse> getOhlcData(List<String> symbols, boolean refresh) {
        log.info("Getting OHLC data for {} symbols with refresh={}", symbols.size(), refresh);
        
        try {
            MarketDataResponseWrapper wrapper = marketDataApiClient.getOhlcDataSync(symbols, refresh);
            if (wrapper == null) {
                log.warn("Received null response wrapper from market data API");
                return Map.of();
            }
            
            Map<String, MarketDataResponse> data = wrapper.getData();
            if (data == null) {
                log.warn("Received null data map from market data API");
                return Map.of();
            }
            
            return data;
        } catch (Exception e) {
            log.error("Error fetching OHLC data: {}", e.getMessage(), e);
            return Map.of();
        }
    }
    
    /**
     * Get OHLC data for the specified symbols with default refresh=true.
     * 
     * @param symbols List of symbols to fetch data for
     * @return Map of symbols to their respective market data
     */
    public Map<String, MarketDataResponse> getOhlcData(List<String> symbols) {
        return getOhlcData(symbols, true);
    }
    
    /**
     * Get OHLC data for the specified symbols asynchronously.
     * 
     * @param symbols List of symbols to fetch data for
     * @param refresh Whether to refresh the data or use cached data
     * @return CompletableFuture containing a map of symbols to their respective market data
     */
    public CompletableFuture<Map<String, MarketDataResponse>> getOhlcDataAsync(List<String> symbols, boolean refresh) {
        log.info("Getting OHLC data asynchronously for {} symbols with refresh={}", symbols.size(), refresh);
        
        return marketDataApiClient.getOhlcData(symbols, refresh)
            .subscribeOn(Schedulers.boundedElastic())
            .map(wrapper -> {
                if (wrapper == null) {
                    log.warn("Received null response wrapper from market data API (async)");
                    return Map.<String, MarketDataResponse>of();
                }
                
                Map<String, MarketDataResponse> data = wrapper.getData();
                if (data == null) {
                    log.warn("Received null data map from market data API (async)");
                    return Map.<String, MarketDataResponse>of();
                }
                
                return data;
            })
            .onErrorResume(e -> {
                log.error("Error fetching OHLC data asynchronously: {}", e.getMessage(), e);
                return reactor.core.publisher.Mono.just(Map.<String, MarketDataResponse>of());
            })
            .toFuture();
    }
    
    /**
     * Get OHLC data for the specified symbols asynchronously with default refresh=true.
     * 
     * @param symbols List of symbols to fetch data for
     * @return CompletableFuture containing a map of symbols to their respective market data
     */
    public CompletableFuture<Map<String, MarketDataResponse>> getOhlcDataAsync(List<String> symbols) {
        return getOhlcDataAsync(symbols, true);
    }
    
    /**
     * Get the current price for a specific symbol.
     * 
     * @param symbol The symbol to get the price for
     * @param refresh Whether to refresh the data or use cached data
     * @return The current price, or null if not available
     */
    public Double getCurrentPrice(String symbol, boolean refresh) {
        log.info("Getting current price for symbol: {} with refresh={}", symbol, refresh);
        
        Map<String, MarketDataResponse> data = getOhlcData(List.of(symbol), refresh);
        MarketDataResponse response = data.get(symbol);
        return response != null ? response.getLastPrice() : null;
    }
    
    /**
     * Get the current price for a specific symbol with default refresh=true.
     * 
     * @param symbol The symbol to get the price for
     * @return The current price, or null if not available
     */
    public Double getCurrentPrice(String symbol) {
        return getCurrentPrice(symbol, true);
    }
    
    /**
     * Get current prices for multiple symbols.
     * 
     * @param symbols List of symbols to fetch prices for
     * @return Map of symbols to their respective current prices
     */
    public Map<String, Double> getCurrentPrices(List<String> symbols) {
        log.info("Getting current prices for {} symbols", symbols.size());
        
        return marketDataApiClient.getCurrentPrices(symbols);
    }
}
