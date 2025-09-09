package com.portfolio.marketdata.service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.portfolio.marketdata.client.MarketDataApiClient;
import com.portfolio.marketdata.model.FilterType;
import com.portfolio.marketdata.model.HistoricalDataRequest;
import com.portfolio.marketdata.model.HistoricalDataRequest.HistoricalDataRequestBuilder;
import com.portfolio.marketdata.model.HistoricalDataResponse;
import com.portfolio.marketdata.model.HistoricalDataResponseWrapper;
import com.portfolio.marketdata.model.InstrumentType;
import com.portfolio.marketdata.model.MarketDataResponse;
import com.portfolio.marketdata.model.MarketDataResponseWrapper;
import com.portfolio.marketdata.util.MarketDataConverter;
import com.portfolio.model.market.MarketData;
import com.portfolio.model.market.TimeFrame;

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
     * Convert historical data responses to market data responses.
     * This allows analytics providers to use the same interface for both current and historical data.
     * 
     * @param historicalData Map of symbols to their historical data responses
     * @return Map of symbols to their market data responses
     */
    public Map<String, MarketData> convertHistoricalToMarketData(Map<String, HistoricalDataResponse> historicalData) {
        if (historicalData == null || historicalData.isEmpty()) {
            return Collections.emptyMap();
        }
        
        Map<String, MarketData> marketDataMap = new HashMap<>();
        historicalData.forEach((symbol, response) -> {
            String cleanedSymbol = cleanSymbol(symbol);
            marketDataMap.put(cleanedSymbol, MarketDataConverter.fromHistoricalDataResponse(response));
        });
        
        return marketDataMap;
    }

    /**
     * Get historical market data for the specified symbols with various filtering options.
     * 
     * @param request The historical data request parameters
     * @return Map of symbols to their respective market data
     */
    public Map<String, MarketData> getHistoricalData(HistoricalDataRequest request) {
        
        log.info("Getting historical data for {} from {} to {} with interval={}, filterType={}", 
                request.getSymbols(), request.getFromDate(), request.getToDate(), 
                request.getInterval(), request.getFilterType());
        
        try {
            HistoricalDataResponseWrapper response = marketDataApiClient.getHistoricalData(request).block();
            
            if (response == null || response.getData() == null) {
                log.warn("No historical data returned for symbols: {}", request.getSymbols());
                return java.util.Collections.emptyMap();
            }
            
            log.info("Successfully fetched historical data for {} symbols with {} total data points", 
                    response.getSuccessfulSymbols(), response.getTotalDataPoints());
            
            // Convert HistoricalDataResponse to MarketData
            Map<String, MarketData> marketDataMap = new HashMap<>();
            for (Map.Entry<String, HistoricalDataResponse> entry : response.getData().entrySet()) {
                marketDataMap.put(entry.getKey(), MarketDataConverter.fromHistoricalDataResponse(entry.getValue()));
            }
            
            return marketDataMap;
            
        } catch (Exception e) {
            log.error("Error fetching historical data: {}", e.getMessage(), e);
            return java.util.Collections.<String, MarketData>emptyMap();
        }
    }

    /**
     * Cleans a symbol by removing exchange prefixes like NSE:, BSE:, etc.
     * 
     * @param symbol The symbol with potential exchange prefix
     * @return The cleaned symbol without the exchange prefix
     */
    private String cleanSymbol(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            return symbol;
        }
        
        // Check for exchange prefix pattern (like NSE:, BSE:, etc.)
        int colonIndex = symbol.indexOf(':');
        if (colonIndex > 0 && colonIndex < symbol.length() - 1) {
            return symbol.substring(colonIndex + 1);
        }
        
        return symbol;
    }
    
    private Map<String, MarketData> convertToMarketDataMap(MarketDataResponseWrapper wrapper, boolean isAsync) {
        if (wrapper == null) {
            log.warn("Received null response wrapper from market data API {}", isAsync ? "(async)" : "");
            return Map.of();
        }
        
        Map<String, MarketDataResponse> responseData = wrapper.getData();
        if (responseData == null) {
            log.warn("Received null data map from market data API {}", isAsync ? "(async)" : "");
            return Map.of();
        }
        
        // Convert MarketDataResponse to MarketData
        Map<String, MarketData> marketDataMap = new HashMap<>();
        responseData.forEach((symbol, response) -> {
            String cleanedSymbol = cleanSymbol(symbol);
            marketDataMap.put(cleanedSymbol, MarketDataConverter.fromMarketDataResponse(response));
        });
        
        return marketDataMap;
    }
    
    /**
     * Get OHLC data for the specified symbols.
     * 
     * @param symbols List of symbols to fetch data for
     * @param refresh Whether to refresh the data or use cached data
     * @return Map of symbols to their respective market data
     */
    public Map<String, MarketData> getOhlcData(List<String> symbols, boolean refresh) {
        log.info("Getting OHLC data for {} symbols with refresh={}", symbols.size(), refresh);
        
        try {
            MarketDataResponseWrapper wrapper = marketDataApiClient.getOhlcData(symbols, TimeFrame.FIVE_MIN.getValue(), refresh).block();
            return convertToMarketDataMap(wrapper, false);
        } catch (Exception e) {
            log.error("Error fetching OHLC data: {}", e.getMessage(), e);
            return Map.of();
        }
    }
    
    
    /**
     * Get OHLC data for the specified symbols asynchronously.
     * 
     * @param symbols List of symbols to fetch data for
     * @param refresh Whether to refresh the data or use cached data
     * @return CompletableFuture containing a map of symbols to their respective market data
     */
    public CompletableFuture<Map<String, MarketData>> getOhlcDataAsync(List<String> symbols, boolean refresh) {
        log.info("Getting OHLC data asynchronously for {} symbols with refresh={}", symbols.size(), refresh);
        
        return marketDataApiClient.getOhlcData(symbols, TimeFrame.FIVE_MIN.getValue(), refresh)
            .subscribeOn(Schedulers.boundedElastic())
            .map(wrapper -> convertToMarketDataMap(wrapper, true))
            .onErrorResume(e -> {
                log.error("Error fetching OHLC data asynchronously: {}", e.getMessage(), e);
                return Mono.just(Map.of());
            })
            .toFuture();
    }
    
    /**
     * Get current prices for multiple symbols.
     * 
     * @param symbols List of symbols to fetch prices for
     * @return Map of symbols to their respective current prices
     */
    public Map<String, Double> getCurrentPrices(List<String> symbols) {
        Map<String, MarketData> data = getOhlcData(symbols, false);
        return data.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().getLastPrice()
            ));
    }

    /**
     * Get historical market data for the specified symbols with various filtering options.
     * This is the main method for retrieving historical data with all possible parameters.
     * 
     * @param symbols List of symbols to fetch historical data for
     * @param fromDate Start date for historical data (inclusive)
     * @param toDate End date for historical data (inclusive)
     * @param timeFrame Time interval for data points (e.g., DAY, FIFTEEN_MIN), defaults to DAY if null
     * @param instrumentType Instrument type (e.g., STOCK for equity), defaults to STOCK if null
     * @param filterType Type of filtering to apply (ALL, START_END, CUSTOM), defaults to ALL if null
     * @param filterFrequency Frequency for CUSTOM filtering (required when filterType is CUSTOM)
     * @param continuous Whether to use continuous data
     * @param forceRefresh Whether to force refresh the data instead of using cache
     * @return Map of symbols to their respective market data
     */
    public Map<String, MarketData> getHistoricalData(
            List<String> symbols, 
            LocalDate fromDate, 
            LocalDate toDate, 
            TimeFrame timeFrame, 
            InstrumentType instrumentType, 
            FilterType filterType,
            Integer filterFrequency,
            Boolean continuous,
            Boolean forceRefresh) {
        
        // Set default values if parameters are null
        TimeFrame tf = timeFrame != null ? timeFrame : TimeFrame.DAY;
        InstrumentType it = instrumentType != null ? instrumentType : InstrumentType.STOCK;
        FilterType ft = filterType != null ? filterType : FilterType.ALL;
        
        HistoricalDataRequest request = HistoricalDataRequest.builder()
                .symbols(String.join(",", symbols))
                .fromDate(fromDate)
                .toDate(toDate)
                .interval(tf.getValue())
                .instrumentType(it.getValue())
                .filterType(ft.getValue())
                .build();
                
        if (filterFrequency != null || continuous != null || forceRefresh != null) {
            HistoricalDataRequestBuilder builder = HistoricalDataRequest.builder()
                    .symbols(request.getSymbols())
                    .fromDate(request.getFromDate())
                    .toDate(request.getToDate())
                    .interval(request.getInterval())
                    .instrumentType(request.getInstrumentType())
                    .filterType(request.getFilterType());
                    
            if (filterFrequency != null) {
                builder.filterFrequency(filterFrequency);
            }
            
            if (continuous != null) {
                builder.continuous(continuous);
            }
            
            if (forceRefresh != null) {
                builder.forceRefresh(forceRefresh);
            }
            
            request = builder.build();
        }
        
        return getHistoricalData(request);
    }

    public Map<String, MarketData> getMarketData(List<String> symbols) {
        if (symbols.isEmpty()) {
            return Collections.emptyMap();
        }
        
        log.info("Fetching market data for {} symbols", symbols.size());
        try {
            Map<String, MarketData> marketData = getOhlcData(symbols, false);
            if (marketData == null) {
                log.warn("Market data service returned null response");
                return Collections.emptyMap();
            }
            return marketData;
        } catch (Exception e) {
            log.error("Error fetching market data: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

}
