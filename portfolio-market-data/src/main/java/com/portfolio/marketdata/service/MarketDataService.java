package com.portfolio.marketdata.service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.portfolio.marketdata.client.MarketDataApiClient;
import com.portfolio.marketdata.model.FilterType;
import com.portfolio.marketdata.model.HistoricalData;
import com.portfolio.marketdata.model.HistoricalDataRequest;
import com.portfolio.marketdata.model.HistoricalDataResponse;
import com.portfolio.marketdata.model.HistoricalDataResponseWrapper;
import com.portfolio.marketdata.model.InstrumentType;
import com.portfolio.marketdata.model.MarketDataResponse;
import com.portfolio.marketdata.model.MarketDataResponseWrapper;
import com.portfolio.marketdata.model.TimeFrame;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
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
     * @param request The historical data request parameters
     * @return Map of symbols to their respective historical data responses
     */
    public Map<String, HistoricalDataResponse> getHistoricalData(HistoricalDataRequest request) {
        
        log.info("Getting historical data for {} symbols from {} to {} with interval={}, filterType={}", 
                request.getSymbols().size(), request.getFromDate(), request.getToDate(), 
                request.getTimeFrame(), request.getFilterType());
        
        try {
            HistoricalDataResponseWrapper response = marketDataApiClient.getHistoricalDataSync(request);
            
            if (response == null || response.getData() == null) {
                log.warn("No historical data returned for symbols: {}", String.join(",", request.getSymbols()));
                return java.util.Collections.emptyMap();
            }
            
            return response.getData();
            
        } catch (Exception e) {
            log.error("Error fetching historical data: {}", e.getMessage(), e);
            return java.util.Collections.emptyMap();
        }
    }
    
    /**
     * Get historical market data for the specified symbols with default filtering options.
     * Uses default values for instrumentType (EQ), filterType (ALL), and interval (DAY).
     * 
     * @param symbols List of symbols to fetch historical data for
     * @param fromDate Start date for historical data (inclusive)
     * @param toDate End date for historical data (inclusive)
     * @return Map of symbols to their respective historical data responses
     */
    public Map<String, HistoricalDataResponse> getHistoricalData(
            List<String> symbols, 
            LocalDate fromDate, 
            LocalDate toDate) {
        
        return getHistoricalData(HistoricalDataRequest.builder()
                .symbols(symbols)
                .fromDate(fromDate)
                .toDate(toDate)
                .timeFrame(TimeFrame.DAY)
                .instrumentType(InstrumentType.EQ)
                .filterType(FilterType.ALL)
                .build());
    }
    
    /**
     * Get historical market data for the specified symbols with various filtering options.
     * 
     * @param symbols List of symbols to fetch historical data for
     * @param fromDate Start date for historical data (inclusive)
     * @param toDate End date for historical data (inclusive)
     * @param timeFrame Time interval for data points (e.g., DAY, FIFTEEN_MIN)
     * @param instrumentType Instrument type (e.g., EQ for equity)
     * @param filterType Type of filtering to apply (ALL, START_END, CUSTOM)
     * @return Map of symbols to their respective historical data responses
     */
    public Map<String, HistoricalDataResponse> getHistoricalData(
            List<String> symbols, 
            LocalDate fromDate, 
            LocalDate toDate, 
            TimeFrame timeFrame, 
            InstrumentType instrumentType, 
            FilterType filterType) {
        
        return getHistoricalData(HistoricalDataRequest.builder()
                .symbols(symbols)
                .fromDate(fromDate)
                .toDate(toDate)
                .timeFrame(timeFrame)
                .instrumentType(instrumentType)
                .filterType(filterType)
                .build());
    }
    
    /**
     * Get historical market data for a single symbol with default filtering options.
     * Uses default values for instrumentType (EQ), filterType (ALL), and interval (DAY).
     * 
     * @param symbol Symbol to fetch historical data for
     * @param fromDate Start date for historical data (inclusive)
     * @param toDate End date for historical data (inclusive)
     * @return Historical data response for the symbol, or null if not found
     */
    public HistoricalDataResponse getHistoricalDataForSymbol(
            String symbol, 
            LocalDate fromDate, 
            LocalDate toDate) {
        
        HistoricalDataRequest request = HistoricalDataRequest.builder()
                .symbols(java.util.Collections.singletonList(symbol))
                .fromDate(fromDate)
                .toDate(toDate)
                .timeFrame(TimeFrame.DAY)
                .instrumentType(InstrumentType.EQ)
                .filterType(FilterType.ALL)
                .build();
        
        Map<String, HistoricalDataResponse> dataMap = getHistoricalData(request);
        
        return dataMap.get(symbol);
    }
    
    /**
     * Get historical market data for a single symbol with various filtering options.
     * 
     * @param symbol Symbol to fetch historical data for
     * @param fromDate Start date for historical data (inclusive)
     * @param toDate End date for historical data (inclusive)
     * @param timeFrame Time interval for data points (e.g., DAY, FIFTEEN_MIN)
     * @param instrumentType Instrument type (e.g., EQ for equity)
     * @param filterType Type of filtering to apply (ALL, START_END, CUSTOM)
     * @param filterFrequency Frequency for CUSTOM filtering (required when filterType is CUSTOM)
     * @param continuous Whether to use continuous data (optional)
     * @return Historical data response for the symbol, or null if not found
     */
    public HistoricalDataResponse getHistoricalDataForSymbol(
            String symbol, 
            LocalDate fromDate, 
            LocalDate toDate, 
            TimeFrame timeFrame, 
            InstrumentType instrumentType, 
            FilterType filterType, 
            Integer filterFrequency,
            Boolean continuous) {
        
        HistoricalDataRequest request = HistoricalDataRequest.builder()
                .symbols(java.util.Collections.singletonList(symbol))
                .fromDate(fromDate)
                .toDate(toDate)
                .timeFrame(timeFrame)
                .instrumentType(instrumentType)
                .filterType(filterType)
                .filterFrequency(filterFrequency)
                .continuous(continuous)
                .build();
        
        Map<String, HistoricalDataResponse> dataMap = getHistoricalData(request);
        
        return dataMap.get(symbol);
    }
    
    /**
     * Get historical market data for the specified symbols asynchronously.
     * 
     * @param request The historical data request parameters
     * @return CompletableFuture containing a map of symbols to their respective historical data responses
     */
    public CompletableFuture<Map<String, HistoricalDataResponse>> getHistoricalDataAsync(HistoricalDataRequest request) {
        
        log.info("Getting historical data asynchronously for {} symbols from {} to {} with interval={}, filterType={}", 
                request.getSymbols().size(), request.getFromDate(), request.getToDate(), 
                request.getTimeFrame(), request.getFilterType());
        
        return marketDataApiClient.getHistoricalData(request)
                .subscribeOn(Schedulers.boundedElastic())
                .map(wrapper -> {
                    if (wrapper == null || wrapper.getData() == null) {
                        log.warn("No historical data returned for symbols: {}", String.join(",", request.getSymbols()));
                        return Collections.<String, HistoricalDataResponse>emptyMap();
                    }
                    
                    Map<String, HistoricalDataResponse> data = wrapper.getData();
                    if (data == null) {
                        log.warn("Received null data map from market data API (async)");
                        return Collections.<String, HistoricalDataResponse>emptyMap();
                    }
                    
                    log.info("Successfully fetched historical data asynchronously for {} symbols with {} total data points", 
                            wrapper.getSuccessfulSymbols(), wrapper.getTotalDataPoints());
                    
                    return data;
                })
                .onErrorResume(e -> {
                    log.error("Error fetching historical data asynchronously: {}", e.getMessage(), e);
                    return Mono.just(Collections.<String, HistoricalDataResponse>emptyMap());
                })
                .toFuture();
    }
    

    
    /**
     * Get historical market data for the specified symbols asynchronously.
     * 
     * @param symbols List of symbols to fetch historical data for
     * @param fromDate Start date for historical data (inclusive)
     * @param toDate End date for historical data (inclusive)
     * @param timeFrame Time interval for data points (e.g., DAY, FIFTEEN_MIN)
     * @param instrumentType Instrument type (e.g., EQ for equity)
     * @param filterType Type of filtering to apply (ALL, START_END, CUSTOM)
     * @param filterFrequency Frequency for CUSTOM filtering (required when filterType is CUSTOM)
     * @param continuous Whether to use continuous data (optional)
     * @return CompletableFuture containing a map of symbols to their respective historical data responses
     */
    public CompletableFuture<Map<String, HistoricalDataResponse>> getHistoricalDataAsync(
            List<String> symbols, 
            LocalDate fromDate, 
            LocalDate toDate, 
            TimeFrame timeFrame, 
            InstrumentType instrumentType, 
            FilterType filterType, 
            Integer filterFrequency,
            Boolean continuous) {
        
        return getHistoricalDataAsync(HistoricalDataRequest.builder()
                .symbols(symbols)
                .fromDate(fromDate)
                .toDate(toDate)
                .timeFrame(timeFrame)
                .instrumentType(instrumentType)
                .filterType(filterType)
                .filterFrequency(filterFrequency)
                .continuous(continuous)
                .build());
    }
    
    /**
     * Get historical market data for the specified symbols asynchronously.
     * Simplified version with fewer parameters and defaults for filterFrequency and continuous.
     * 
     * @param symbols List of symbols to fetch historical data for
     * @param fromDate Start date for historical data (inclusive)
     * @param toDate End date for historical data (inclusive)
     * @param timeFrame Time interval for data points (e.g., DAY, FIFTEEN_MIN)
     * @param instrumentType Instrument type (e.g., EQ for equity)
     * @param filterType Type of filtering to apply (ALL, START_END, CUSTOM)
     * @return CompletableFuture containing a map of symbols to their respective historical data responses
     */
    public CompletableFuture<Map<String, HistoricalDataResponse>> getHistoricalDataAsync(
            List<String> symbols, 
            LocalDate fromDate, 
            LocalDate toDate, 
            TimeFrame timeFrame, 
            InstrumentType instrumentType, 
            FilterType filterType) {
        
        return getHistoricalDataAsync(HistoricalDataRequest.builder()
                .symbols(symbols)
                .fromDate(fromDate)
                .toDate(toDate)
                .timeFrame(timeFrame)
                .instrumentType(instrumentType)
                .filterType(filterType)
                .build());
    }
}
