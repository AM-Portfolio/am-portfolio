package com.portfolio.marketdata.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
     * @return Map of symbols to their respective market data
     */
    public Map<String, MarketDataResponse> getOhlcData(List<String> symbols) {
        log.info("Getting OHLC data for {} symbols", symbols.size());
        
        return marketDataApiClient.getOhlcDataSync(symbols)
            .getData();
    }
    
    /**
     * Get OHLC data for the specified symbols asynchronously.
     * 
     * @param symbols List of symbols to fetch data for
     * @return CompletableFuture containing a map of symbols to their respective market data
     */
    public CompletableFuture<Map<String, MarketDataResponse>> getOhlcDataAsync(List<String> symbols) {
        log.info("Getting OHLC data asynchronously for {} symbols", symbols.size());
        
        return marketDataApiClient.getOhlcData(symbols)
            .subscribeOn(Schedulers.boundedElastic())
            .map(MarketDataResponseWrapper::getData)
            .toFuture();
    }
    
    /**
     * Get the current price for a specific symbol.
     * 
     * @param symbol The symbol to get the price for
     * @return The current price, or null if not available
     */
    public Double getCurrentPrice(String symbol) {
        log.info("Getting current price for symbol: {}", symbol);
        
        Map<String, MarketDataResponse> data = getOhlcData(List.of(symbol));
        MarketDataResponse response = data.get(symbol);
        return response != null ? response.getLastPrice() : null;
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
