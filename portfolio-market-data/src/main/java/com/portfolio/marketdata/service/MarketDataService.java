package com.portfolio.marketdata.service;

import java.time.LocalDate;
import java.util.Arrays;
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
public class MarketDataService {

    private final MarketDataApiClient marketDataApiClient;
    
    @org.springframework.lang.Nullable
    private final com.portfolio.redis.service.PortfolioMarketDataRedisService marketDataRedisService;

    private final java.util.concurrent.Executor taskExecutor;

    public MarketDataService(
            MarketDataApiClient marketDataApiClient,
            @org.springframework.lang.Nullable com.portfolio.redis.service.PortfolioMarketDataRedisService marketDataRedisService,
            @org.springframework.beans.factory.annotation.Qualifier("taskExecutor") java.util.concurrent.Executor taskExecutor) {
        this.marketDataApiClient = marketDataApiClient;
        this.marketDataRedisService = marketDataRedisService;
        this.taskExecutor = taskExecutor;
    }

    /**
     * Convert historical data responses to market data responses.
     * This allows analytics providers to use the same interface for both current
     * and historical data.
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
     * Get historical market data for the specified symbols with various filtering
     * options.
     * 
     * @param request The historical data request parameters
     * @return Map of symbols to their respective market data
     */
    public Map<String, MarketData> getHistoricalData(HistoricalDataRequest request) {

        String symbolStr = request.getSymbols();
        if (symbolStr == null || symbolStr.trim().isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        List<String> validSymbols = Arrays.stream(symbolStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        if (validSymbols.isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        log.info("Getting historical data for {} symbols from {} to {} with interval={}, filterType={}",
                validSymbols.size(), request.getFromDate(), request.getToDate(),
                request.getInterval(), request.getFilterType());

        final int CHUNK_SIZE = 20;
        List<List<String>> chunks = new java.util.ArrayList<>();
        for (int i = 0; i < validSymbols.size(); i += CHUNK_SIZE) {
            chunks.add(validSymbols.subList(i, Math.min(i + CHUNK_SIZE, validSymbols.size())));
        }

        List<java.util.concurrent.CompletableFuture<Map<String, MarketData>>> futures = chunks.stream()
            .map(chunk -> java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    HistoricalDataRequest chunkRequest = HistoricalDataRequest.builder()
                            .symbols(String.join(",", chunk))
                            .fromDate(request.getFromDate())
                            .toDate(request.getToDate())
                            .interval(request.getInterval())
                            .filterType(request.getFilterType())
                            .instrumentType(request.getInstrumentType())
                            .filterFrequency(request.getFilterFrequency())
                            .continuous(request.getContinuous())
                            .forceRefresh(request.getForceRefresh())
                            .additionalParams(request.getAdditionalParams())
                            .build();

                    HistoricalDataResponseWrapper response = marketDataApiClient.getHistoricalData(chunkRequest).block();

                    if (response == null || response.getData() == null) {
                        return java.util.Collections.<String, MarketData>emptyMap();
                    }

                    Map<String, MarketData> chunkResult = new HashMap<>();
                    for (Map.Entry<String, HistoricalDataResponse> entry : response.getData().entrySet()) {
                        chunkResult.put(entry.getKey(), MarketDataConverter.fromHistoricalDataResponse(entry.getValue()));
                    }
                    return chunkResult;
                } catch (Exception e) {
                    log.warn("[HistoricalData chunk] Failed for chunk of {}: {}", chunk.size(), e.getMessage());
                    return java.util.Collections.<String, MarketData>emptyMap();
                }
            }, taskExecutor))
            .collect(Collectors.toList());

        Map<String, MarketData> merged = new java.util.HashMap<>();
        for (java.util.concurrent.CompletableFuture<Map<String, MarketData>> f : futures) {
            try {
                merged.putAll(f.get(30, java.util.concurrent.TimeUnit.SECONDS));
            } catch (Exception e) {
                log.warn("[HistoricalData chunk] Chunk future failed or timed out: {}", e.getMessage());
            }
        }
        
        return merged;
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
     * Splits large symbol lists into parallel chunks of max 20 symbols to prevent
     * downstream API timeouts (previously a 59-symbol single request took 29-30s).
     *
     * @param symbols List of symbols to fetch data for
     * @param refresh Whether to refresh the data or use cached data
     * @return Map of symbols to their respective market data
     */
    public Map<String, MarketData> getOhlcData(List<String> symbols, boolean refresh) {
        return getOhlcData(symbols, TimeFrame.DAY.getValue(), refresh);
    }

    /**
     * Get OHLC data for the specified symbols with a specific time frame.
     * Splits large symbol lists into parallel chunks of max 20 symbols to prevent
     * downstream API timeouts.
     *
     * @param symbols   List of symbols to fetch data for
     * @param timeFrame The time frame for the OHLC data
     * @param refresh   Whether to refresh the data or use cached data
     * @return Map of symbols to their respective market data
     */
    public Map<String, MarketData> getOhlcData(List<String> symbols, String timeFrame, boolean refresh) {
        List<String> validSymbols = symbols.stream()
                .filter(s -> s != null && !s.trim().isEmpty())
                .collect(Collectors.toList());

        if (validSymbols.isEmpty()) {
            return Collections.emptyMap();
        }

        log.info("Getting OHLC data for {} symbols with timeFrame={} refresh={}", validSymbols.size(), timeFrame, refresh);

        // Partition into chunks of 20 to avoid massive single-batch timeouts
        final int CHUNK_SIZE = 20;
        List<List<String>> chunks = new java.util.ArrayList<>();
        for (int i = 0; i < validSymbols.size(); i += CHUNK_SIZE) {
            chunks.add(validSymbols.subList(i, Math.min(i + CHUNK_SIZE, validSymbols.size())));
        }

        // Fire all chunk requests concurrently and merge results
        List<java.util.concurrent.CompletableFuture<Map<String, MarketData>>> futures = chunks.stream()
            .map(chunk -> java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    MarketDataResponseWrapper w = marketDataApiClient
                            .getOhlcData(chunk, timeFrame, refresh).block();
                    return convertToMarketDataMap(w, true);
                } catch (Exception e) {
                    log.warn("[OHLC chunk] Failed for chunk of {}: {}", chunk.size(), e.getMessage());
                    return Collections.<String, MarketData>emptyMap();
                }
            }, taskExecutor))
            .collect(Collectors.toList());

        Map<String, MarketData> merged = new java.util.HashMap<>();
        for (java.util.concurrent.CompletableFuture<Map<String, MarketData>> f : futures) {
            try {
                merged.putAll(f.get(30, java.util.concurrent.TimeUnit.SECONDS));
            } catch (Exception e) {
                log.warn("[OHLC chunk] Chunk future failed or timed out: {}", e.getMessage());
            }
        }
        return merged;
    }

    /**
     * Get OHLC data for the specified symbols asynchronously.
     * 
     * @param symbols List of symbols to fetch data for
     * @param refresh Whether to refresh the data or use cached data
     * @return CompletableFuture containing a map of symbols to their respective
     *         market data
     */
    public CompletableFuture<Map<String, MarketData>> getOhlcDataAsync(List<String> symbols, boolean refresh) {
        log.info("Getting OHLC data asynchronously for {} symbols with refresh={}", symbols.size(), refresh);

        return marketDataApiClient.getOhlcData(symbols, TimeFrame.DAY.getValue(), refresh)
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
     * Guards against null lastPrice to prevent NullPointerException.
     *
     * @param symbols List of symbols to fetch prices for
     * @return Map of symbols to their respective current prices
     */
    public Map<String, Double> getCurrentPrices(List<String> symbols) {
        Map<String, MarketData> data = getOhlcData(symbols, false);
        return data.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().getLastPrice() != null)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getLastPrice()));
    }

    /**
     * Get historical market data for the specified symbols with various filtering
     * options.
     * This is the main method for retrieving historical data with all possible
     * parameters.
     * 
     * @param symbols         List of symbols to fetch historical data for
     * @param fromDate        Start date for historical data (inclusive)
     * @param toDate          End date for historical data (inclusive)
     * @param timeFrame       Time interval for data points (e.g., DAY,
     *                        FIFTEEN_MIN), defaults to DAY if null
     * @param instrumentType  Instrument type (e.g., STOCK for equity), defaults to
     *                        STOCK if null
     * @param filterType      Type of filtering to apply (ALL, START_END, CUSTOM),
     *                        defaults to ALL if null
     * @param filterFrequency Frequency for CUSTOM filtering (required when
     *                        filterType is CUSTOM)
     * @param continuous      Whether to use continuous data
     * @param forceRefresh    Whether to force refresh the data instead of using
     *                        cache
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
                .fromDate(fromDate != null ? fromDate.toString() : null)
                .toDate(toDate != null ? toDate.toString() : null)
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
        if (symbols == null || symbols.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, MarketData> result = new HashMap<>();
        List<String> normalized = symbols.stream().map(this::cleanSymbol).collect(Collectors.toList());

        // 1. Try market data cache first (populated by am-market service or last live fetch)
        if (marketDataRedisService != null) {
            try {
                Map<String, MarketData> cached = marketDataRedisService.getMarketData(normalized);
                if (cached != null) result.putAll(cached);
            } catch (Exception e) {
                log.warn("[MarketData] Cache read failed: {}", e.getMessage());
            }
        }

        // 2. Find symbols still missing after cache lookup
        List<String> missing = normalized.stream()
            .filter(s -> !result.containsKey(s))
            .collect(Collectors.toList());

        if (missing.isEmpty()) {
            log.info("[MarketData] All {} symbols served from cache.", result.size());
            return result;
        }

        // 3. Fetch missing from OHLC API
        log.info("[MarketData] Cache miss for {} symbols. Fetching from API.", missing.size());
        try {
            Map<String, MarketData> fetched = getOhlcData(missing, false);
            if (fetched != null && !fetched.isEmpty()) {
                result.putAll(fetched);
                // Store in cache with SmartTTL
                if (marketDataRedisService != null) {
                    marketDataRedisService.cacheMarketData(fetched);
                }
            }
        } catch (Exception e) {
            log.error("[MarketData] OHLC fetch failed: {}", e.getMessage());
        }

        // 4. Fallback for symbols that are STILL missing (e.g., after market hours or OHLC timeout)
        List<String> stillMissing = symbols.stream().map(this::cleanSymbol)
            .filter(s -> {
                MarketData data = result.get(s);
                return data == null || data.getLastPrice() == null || data.getLastPrice() == 0.0;
            })
            .collect(Collectors.toList());
            
        if (!stillMissing.isEmpty()) {
            log.info("[MarketData] OHLC returned empty for {} symbols (likely after hours). Falling back to EOD historical data.", stillMissing.size());
            java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
            HistoricalDataRequest eodRequest = HistoricalDataRequest.builder()
                .symbols(String.join(",", stillMissing))
                .fromDate(today.minusDays(5).toString())
                .toDate(today.toString())
                .interval(TimeFrame.DAY.getValue())
                .filterType(FilterType.START_END.getValue())
                .instrumentType(InstrumentType.EQ.getValue())
                .build();
            try {
                // Apply a strict 15-second timeout to prevent unbounded hanging
                Map<String, MarketData> eodData = java.util.concurrent.CompletableFuture.supplyAsync(() -> getHistoricalData(eodRequest), taskExecutor)
                    .get(15, java.util.concurrent.TimeUnit.SECONDS);
                    
                if (eodData != null && !eodData.isEmpty()) {
                    // Update result with EOD data, preserving any partial live data
                    eodData.forEach((k, v) -> {
                        if (v != null && v.getLastPrice() != null && v.getLastPrice() > 0) {
                            // Normalize the key in case the API returns prefixed symbols (e.g. NSE:RELIANCE)
                            result.put(cleanSymbol(k), v);
                        }
                    });
                    if (marketDataRedisService != null) {
                        marketDataRedisService.cacheMarketData(result); // Cache the combined result
                    }
                }
            } catch (java.util.concurrent.TimeoutException ex) {
                log.warn("[MarketData] EOD historical fallback timed out after 15s");
            } catch (Exception ex) {
                log.warn("[MarketData] Failed to fetch EOD historical fallback for missing symbols: {}", ex.getMessage());
            }
        }

        log.info("[MarketData] Result: {}/{} symbols returned.", result.size(), symbols.size());
        return result;
    }

    /**
     * Fetch market cap data for a list of symbols using batch search.
     * 
     * @param symbols List of symbols
     * @return Map of Symbol -> SecurityMatch (containing market cap info)
     */
    public Map<String, com.portfolio.marketdata.model.BatchSearchResponse.SecurityMatch> getMarketCapData(
            List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Collections.emptyMap();
        }

        log.info("Fetching market cap data for {} symbols", symbols.size());

        final int CHUNK_SIZE = 20;
        List<List<String>> chunks = new java.util.ArrayList<>();
        for (int i = 0; i < symbols.size(); i += CHUNK_SIZE) {
            chunks.add(symbols.subList(i, Math.min(i + CHUNK_SIZE, symbols.size())));
        }

        List<java.util.concurrent.CompletableFuture<Map<String, com.portfolio.marketdata.model.BatchSearchResponse.SecurityMatch>>> futures = chunks.stream()
            .map(chunk -> java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    com.portfolio.marketdata.model.BatchSearchRequest request = com.portfolio.marketdata.model.BatchSearchRequest
                            .builder()
                            .queries(chunk)
                            .limit(1) // We only need the best match per symbol (usually exact match)
                            .minMatchScore(0.9) // High confidence for exact symbol match
                            .build();

                    com.portfolio.marketdata.model.BatchSearchResponse response = marketDataApiClient.batchSearch(request)
                            .block();

                    if (response == null || response.getResults() == null) {
                        return Collections.<String, com.portfolio.marketdata.model.BatchSearchResponse.SecurityMatch>emptyMap();
                    }

                    Map<String, com.portfolio.marketdata.model.BatchSearchResponse.SecurityMatch> chunkResult = new HashMap<>();
                    for (com.portfolio.marketdata.model.BatchSearchResponse.QueryResult qr : response.getResults()) {
                        if (qr.getMatches() != null && !qr.getMatches().isEmpty()) {
                            // Assuming the first match is the correct one for the query (symbol)
                            chunkResult.put(qr.getQuery(), qr.getMatches().get(0));
                        }
                    }
                    return chunkResult;
                } catch (Exception e) {
                    log.error("[MarketCap chunk] Error fetching market cap data for {} symbols: {}", chunk.size(), e.getMessage());
                    return Collections.<String, com.portfolio.marketdata.model.BatchSearchResponse.SecurityMatch>emptyMap();
                }
            }, taskExecutor))
            .collect(Collectors.toList());

        Map<String, com.portfolio.marketdata.model.BatchSearchResponse.SecurityMatch> merged = new java.util.HashMap<>();
        for (java.util.concurrent.CompletableFuture<Map<String, com.portfolio.marketdata.model.BatchSearchResponse.SecurityMatch>> f : futures) {
            try {
                merged.putAll(f.get(15, java.util.concurrent.TimeUnit.SECONDS));
            } catch (Exception e) {
                log.warn("[MarketCap chunk] Chunk future failed or timed out: {}", e.getMessage());
            }
        }

        return merged;
    }

    /**
     * Gets historical charts data for the specified symbols and range.
     * 
     * @param symbols list of symbols
     * @param range time range (e.g. 1D, 1M, 1Y)
     * @return HistoricalChartsResponse containing the chart points
     */
    public com.portfolio.marketdata.model.HistoricalChartsResponse getHistoricalCharts(List<String> symbols, String range) {
        if (symbols == null || symbols.isEmpty()) {
            return new com.portfolio.marketdata.model.HistoricalChartsResponse();
        }

        List<String> validSymbols = symbols.stream()
                .filter(s -> s != null && !s.trim().isEmpty())
                .collect(Collectors.toList());

        if (validSymbols.isEmpty()) {
            return new com.portfolio.marketdata.model.HistoricalChartsResponse();
        }

        log.info("Getting historical charts for {} symbols with range={}", validSymbols.size(), range);

        final int CHUNK_SIZE = 20;
        List<List<String>> chunks = new java.util.ArrayList<>();
        for (int i = 0; i < validSymbols.size(); i += CHUNK_SIZE) {
            chunks.add(validSymbols.subList(i, Math.min(i + CHUNK_SIZE, validSymbols.size())));
        }

        List<java.util.concurrent.CompletableFuture<com.portfolio.marketdata.model.HistoricalChartsResponse>> futures = chunks.stream()
            .map(chunk -> java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    return marketDataApiClient.getHistoricalCharts(chunk, range).block();
                } catch (Exception e) {
                    log.error("[HistoricalCharts chunk] Failed for chunk of {}: {}", chunk.size(), e.getMessage());
                    return new com.portfolio.marketdata.model.HistoricalChartsResponse();
                }
            }, taskExecutor))
            .collect(Collectors.toList());

        com.portfolio.marketdata.model.HistoricalChartsResponse mergedResponse = new com.portfolio.marketdata.model.HistoricalChartsResponse();
        mergedResponse.setData(new java.util.HashMap<>());

        for (java.util.concurrent.CompletableFuture<com.portfolio.marketdata.model.HistoricalChartsResponse> f : futures) {
            try {
                com.portfolio.marketdata.model.HistoricalChartsResponse chunkResult = f.get(30, java.util.concurrent.TimeUnit.SECONDS);
                if (chunkResult != null && chunkResult.getData() != null) {
                    mergedResponse.getData().putAll(chunkResult.getData());
                }
            } catch (Exception e) {
                log.warn("[HistoricalCharts chunk] Chunk future failed or timed out: {}", e.getMessage());
            }
        }
        
        return mergedResponse;
    }
}
