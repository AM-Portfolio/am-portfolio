package com.portfolio.marketdata.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.portfolio.marketdata.client.MarketDataApiClient;
import com.portfolio.marketdata.model.HistoricalData;
import com.portfolio.marketdata.model.HistoricalDataResponse;
import com.portfolio.marketdata.model.HistoricalDataResponseWrapper;
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
    
    /**
     * Get historical market data for the specified symbols with various filtering options.
     * 
     * @param symbols List of symbols to fetch historical data for
     * @param fromDate Start date for historical data (inclusive)
     * @param toDate End date for historical data (inclusive)
     * @param interval Time interval for data points (e.g., "day", "15min")
     * @param instrumentType Instrument type (e.g., "EQ" for equity)
     * @param filterType Type of filtering to apply ("ALL", "START_END", "CUSTOM")
     * @param filterFrequency Frequency for CUSTOM filtering (required when filterType is CUSTOM)
     * @param continuous Whether to use continuous data (optional)
     * @return Map of symbols to their respective historical data responses
     */
    public Map<String, HistoricalData> getHistoricalData(
            List<String> symbols, 
            LocalDate fromDate, 
            LocalDate toDate, 
            String interval, 
            String instrumentType, 
            String filterType, 
            Integer filterFrequency,
            Boolean continuous) {
        
        log.info("Getting historical data for {} symbols from {} to {} with interval={}, filterType={}", 
                symbols.size(), fromDate, toDate, interval, filterType);
        
        try {
            HistoricalDataResponseWrapper wrapper = marketDataApiClient.getHistoricalDataSync(
                    symbols, fromDate, toDate, interval, instrumentType, filterType, filterFrequency, continuous);
            
            if (wrapper == null) {
                log.warn("Received null response wrapper from market data API");
                return Map.of();
            }
            
            Map<String, HistoricalDataResponse> responseData = wrapper.getData();
            if (responseData == null) {
                log.warn("Received null data map from market data API");
                return Map.of();
            }
            
            // Extract HistoricalData from each HistoricalDataResponse
            Map<String, HistoricalData> data = responseData.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().getData()
                    ));
            
            log.info("Successfully fetched historical data for {} symbols with {} total data points", 
                    wrapper.getSuccessfulSymbols(), wrapper.getTotalDataPoints());
            
            return data;
        } catch (Exception e) {
            log.error("Error fetching historical data: {}", e.getMessage(), e);
            return Map.of();
        }
    }
    
    /**
     * Get historical market data for the specified symbols with various filtering options.
     * Simplified version with fewer parameters.
     * 
     * @param symbols List of symbols to fetch historical data for
     * @param fromDate Start date for historical data (inclusive)
     * @param toDate End date for historical data (inclusive)
     * @param interval Time interval for data points (e.g., "day", "15min")
     * @param instrumentType Instrument type (e.g., "EQ" for equity)
     * @param filterType Type of filtering to apply ("ALL", "START_END", "CUSTOM")
     * @return Map of symbols to their respective historical data responses
     */
    public Map<String, HistoricalData> getHistoricalData(
            List<String> symbols, 
            LocalDate fromDate, 
            LocalDate toDate, 
            String interval, 
            String instrumentType, 
            String filterType) {
        
        return getHistoricalData(symbols, fromDate, toDate, interval, instrumentType, filterType, null, null);
    }
    
    /**
     * Get historical market data for the specified symbols asynchronously.
     * 
     * @param symbols List of symbols to fetch historical data for
     * @param fromDate Start date for historical data (inclusive)
     * @param toDate End date for historical data (inclusive)
     * @param interval Time interval for data points (e.g., "day", "15min")
     * @param instrumentType Instrument type (e.g., "EQ" for equity)
     * @param filterType Type of filtering to apply ("ALL", "START_END", "CUSTOM")
     * @param filterFrequency Frequency for CUSTOM filtering (required when filterType is CUSTOM)
     * @param continuous Whether to use continuous data (optional)
     * @return CompletableFuture containing a map of symbols to their respective historical data responses
     */
    public CompletableFuture<Map<String, HistoricalDataResponse>> getHistoricalDataAsync(
            List<String> symbols, 
            LocalDate fromDate, 
            LocalDate toDate, 
            String interval, 
            String instrumentType, 
            String filterType, 
            Integer filterFrequency,
            Boolean continuous) {
        
        log.info("Getting historical data asynchronously for {} symbols from {} to {} with interval={}, filterType={}", 
                symbols.size(), fromDate, toDate, interval, filterType);
        
        return marketDataApiClient.getHistoricalData(symbols, fromDate, toDate, interval, instrumentType, filterType, 
                    filterFrequency, continuous)
                .subscribeOn(Schedulers.boundedElastic())
                .map(wrapper -> {
                    if (wrapper == null) {
                        log.warn("Received null response wrapper from market data API (async)");
                        return Map.<String, HistoricalDataResponse>of();
                    }
                    
                    Map<String, HistoricalDataResponse> data = wrapper.getData();
                    if (data == null) {
                        log.warn("Received null data map from market data API (async)");
                        return Map.<String, HistoricalDataResponse>of();
                    }
                    
                    log.info("Successfully fetched historical data asynchronously for {} symbols with {} total data points", 
                            wrapper.getSuccessfulSymbols(), wrapper.getTotalDataPoints());
                    
                    return data;
                })
                .onErrorResume(e -> {
                    log.error("Error fetching historical data asynchronously: {}", e.getMessage(), e);
                    return reactor.core.publisher.Mono.just(Map.<String, HistoricalDataResponse>of());
                })
                .toFuture();
    }
    
    /**
     * Get historical market data for the specified symbols asynchronously.
     * Simplified version with fewer parameters.
     * 
     * @param symbols List of symbols to fetch historical data for
     * @param fromDate Start date for historical data (inclusive)
     * @param toDate End date for historical data (inclusive)
     * @param interval Time interval for data points (e.g., "day", "15min")
     * @param instrumentType Instrument type (e.g., "EQ" for equity)
     * @param filterType Type of filtering to apply ("ALL", "START_END", "CUSTOM")
     * @return CompletableFuture containing a map of symbols to their respective historical data responses
     */
    public CompletableFuture<Map<String, HistoricalDataResponse>> getHistoricalDataAsync(
            List<String> symbols, 
            LocalDate fromDate, 
            LocalDate toDate, 
            String interval, 
            String instrumentType, 
            String filterType) {
        
        return getHistoricalDataAsync(symbols, fromDate, toDate, interval, instrumentType, filterType, null, null);
    }
}
