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
     * Get OHLC data for the specified symbols.
     * 
     * @param symbols List of symbols to fetch data for
     * @param refresh Whether to refresh the data or use cached data
     * @return Map of symbols to their respective market data
     */
    public Map<String, MarketData> getOhlcData(List<String> symbols, boolean refresh) {
        log.info("Getting OHLC data for {} symbols with refresh={}", symbols.size(), refresh);
        
        try {
            MarketDataResponseWrapper wrapper = marketDataApiClient.getOhlcDataSync(symbols, refresh);
            if (wrapper == null) {
                log.warn("Received null response wrapper from market data API");
                return Map.of();
            }
            
            Map<String, MarketDataResponse> responseData = wrapper.getData();
            if (responseData == null) {
                log.warn("Received null data map from market data API");
                return Map.of();
            }
            
            // Convert MarketDataResponse to MarketData
            Map<String, MarketData> marketDataMap = new HashMap<>();
            responseData.forEach((symbol, response) -> {
                marketDataMap.put(symbol, MarketDataConverter.fromMarketDataResponse(response));
            });
            
            return marketDataMap;
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
    public Map<String, MarketData> getOhlcData(List<String> symbols) {
        return getOhlcData(symbols, false);
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
        
        return marketDataApiClient.getOhlcData(symbols, refresh)
            .subscribeOn(Schedulers.boundedElastic())
            .map(wrapper -> {
                if (wrapper == null) {
                    log.warn("Received null response wrapper from market data API (async)");
                    return Map.<String, MarketData>of();
                }
                
                Map<String, MarketDataResponse> responseData = wrapper.getData();
                if (responseData == null) {
                    log.warn("Received null data map from market data API (async)");
                    return Map.<String, MarketData>of();
                }
                
                // Convert MarketDataResponse to MarketData
                Map<String, MarketData> marketDataMap = new HashMap<>();
                responseData.forEach((symbol, response) -> {
                    marketDataMap.put(symbol, MarketDataConverter.fromMarketDataResponse(response));
                });
                
                return marketDataMap;
            })
            .onErrorResume(e -> {
                log.error("Error fetching OHLC data asynchronously: {}", e.getMessage(), e);
                return Mono.just(Map.<String, MarketData>of());
            })
            .toFuture();
    }
    
    /**
     * Get OHLC data for the specified symbols asynchronously with default refresh=true.
     * 
     * @param symbols List of symbols to fetch data for
     * @return CompletableFuture containing a map of symbols to their respective market data
     */
    public CompletableFuture<Map<String, MarketData>> getOhlcDataAsync(List<String> symbols) {
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
        
        Map<String, MarketData> data = getOhlcData(List.of(symbol), refresh);
        MarketData marketData = data.get(symbol);
        return marketData != null ? marketData.getLastPrice() : null;
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
        Map<String, MarketData> data = getOhlcData(symbols, false);
        return data.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().getLastPrice()
            ));
    }
    
    /**
     * Get historical market data for the specified symbols with various filtering options.
     * 
     * @param request The historical data request parameters
     * @return Map of symbols to their respective market data
     */
    public Map<String, MarketData> getHistoricalData(HistoricalDataRequest request) {
        
        log.info("Getting historical data for {} symbols from {} to {} with interval={}, filterType={}", 
                request.getSymbols().size(), request.getFromDate(), request.getToDate(), 
                request.getTimeFrame(), request.getFilterType());
        
        try {
            HistoricalDataResponseWrapper response = marketDataApiClient.getHistoricalDataSync(request);
            
            if (response == null || response.getData() == null) {
                log.warn("No historical data returned for symbols: {}", String.join(",", request.getSymbols()));
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
     * Get historical market data for the specified symbols with default filtering options.
     * Uses default values for instrumentType (EQ), filterType (ALL), and interval (DAY).
     * 
     * @param symbols List of symbols to fetch historical data for
     * @param fromDate Start date for historical data (inclusive)
     * @param toDate End date for historical data (inclusive)
     * @return Map of symbols to their respective market data
     */
    public Map<String, MarketData> getHistoricalData(
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
     * @return Map of symbols to their respective market data
     */
    public Map<String, MarketData> getHistoricalData(
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
    public MarketData getHistoricalDataForSymbol(
            String symbol, 
            LocalDate fromDate, 
            LocalDate toDate) {
        
        Map<String, MarketData> result = getHistoricalData(
                Collections.singletonList(symbol), 
                fromDate, 
                toDate);
        
        return result.get(symbol);
    }
    
    /**
     * Get historical market data as MarketData objects for the specified symbols.
     * This allows using the same interface for both current and historical market data.
     * 
     * @param symbols List of symbols to fetch historical data for
     * @param fromDate Start date for historical data (inclusive)
     * @param toDate End date for historical data (inclusive)
     * @return Map of symbols to their respective market data
     */
    public Map<String, MarketData> getHistoricalMarketData(
            List<String> symbols, 
            LocalDate fromDate, 
            LocalDate toDate) {
        
        return getHistoricalMarketData(HistoricalDataRequest.builder()
                .symbols(symbols)
                .fromDate(fromDate)
                .toDate(toDate)
                .timeFrame(TimeFrame.DAY)
                .instrumentType(InstrumentType.EQ)
                .filterType(FilterType.ALL)
                .build());
    }
    
    /**
     * Get historical market data as MarketData objects with various filtering options.
     * 
     * @param request The historical data request with all parameters
     * @return Map of symbols to their respective market data
     */
    public Map<String, MarketData> getHistoricalMarketData(HistoricalDataRequest request) {
        // Since getHistoricalData now directly returns MarketData, we can just call it
        return getHistoricalData(request);
    }
    
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
            marketDataMap.put(symbol, MarketDataConverter.fromHistoricalDataResponse(response));
        });
        
        return marketDataMap;
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
    public MarketData getHistoricalDataForSymbol(
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
        
        Map<String, MarketData> dataMap = getHistoricalData(request);
        
        return dataMap.get(symbol);
    }
    
    /**
     * Get historical market data as MarketData objects asynchronously.
     * 
     * @param symbols List of symbols to fetch historical data for
     * @param fromDate Start date for historical data (inclusive)
     * @param toDate End date for historical data (inclusive)
     * @return CompletableFuture containing a map of symbols to their respective market data
     */
    public CompletableFuture<Map<String, MarketData>> getHistoricalMarketDataAsync(
            List<String> symbols, 
            LocalDate fromDate, 
            LocalDate toDate) {
        
        return getHistoricalMarketDataAsync(HistoricalDataRequest.builder()
                .symbols(symbols)
                .fromDate(fromDate)
                .toDate(toDate)
                .timeFrame(TimeFrame.DAY)
                .instrumentType(InstrumentType.EQ)
                .filterType(FilterType.ALL)
                .build());
    }
    
    /**
     * Get historical market data as MarketData objects asynchronously with various filtering options.
     * 
     * @param request The historical data request with all parameters
     * @return CompletableFuture containing a map of symbols to their respective market data
     */
    public CompletableFuture<Map<String, MarketData>> getHistoricalMarketDataAsync(HistoricalDataRequest request) {
        
        log.info("Getting historical data asynchronously for {} symbols from {} to {} with interval={}, filterType={}", 
                request.getSymbols().size(), request.getFromDate(), request.getToDate(), 
                request.getTimeFrame(), request.getFilterType());
        
        return marketDataApiClient.getHistoricalData(request)
                .subscribeOn(Schedulers.boundedElastic())
                .map(wrapper -> {
                    if (wrapper == null || wrapper.getData() == null) {
                        log.warn("No historical data returned for symbols: {}", String.join(",", request.getSymbols()));
                        return Collections.<String, MarketData>emptyMap();
                    }
                    
                    Map<String, HistoricalDataResponse> data = wrapper.getData();
                    return convertHistoricalToMarketData(data);
                })
                .onErrorResume(e -> {
                    log.error("Error fetching historical data asynchronously: {}", e.getMessage(), e);
                    return Mono.just(Collections.<String, MarketData>emptyMap());
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
     * @return CompletableFuture containing a map of symbols to their respective market data
     */
    public CompletableFuture<Map<String, MarketData>> getHistoricalDataAsync(HistoricalDataRequest request) {
        
        return getHistoricalDataAsync(HistoricalDataRequest.builder()
                .symbols(request.getSymbols())
                .fromDate(request.getFromDate())
                .toDate(request.getToDate())
                .timeFrame(request.getTimeFrame())
                .instrumentType(request.getInstrumentType())
                .filterType(request.getFilterType())
                .filterFrequency(request.getFilterFrequency())
                .continuous(request.getContinuous())
                .build());
    }
}
