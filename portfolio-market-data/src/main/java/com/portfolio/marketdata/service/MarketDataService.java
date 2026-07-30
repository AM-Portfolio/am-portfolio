package com.portfolio.marketdata.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.am.common.amcommondata.document.price.StockPriceDocument;
import com.am.common.amcommondata.document.price.StockPriceHistoryDocument;
import com.am.common.investment.model.historical.OHLCVTPoint;
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
import com.portfolio.model.util.SymbolResolver;

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

    @org.springframework.lang.Nullable
    private final com.am.common.amcommondata.service.price.StockPriceMongoService stockPriceMongoService;

    @org.springframework.lang.Nullable
    private final com.am.common.amcommondata.service.price.StockPriceHistoryMongoService stockPriceHistoryMongoService;

    private final com.github.benmanes.caffeine.cache.Cache<String, MarketData> localCache = 
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
            .expireAfterWrite(5, java.util.concurrent.TimeUnit.MINUTES)
            .maximumSize(20000)
            .build();

    private final com.github.benmanes.caffeine.cache.Cache<String, com.portfolio.marketdata.model.HistoricalData> chartCache = 
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
            .expireAfterWrite(3, java.util.concurrent.TimeUnit.MINUTES)
            .maximumSize(5000)
            .build();

    private final java.util.concurrent.Executor taskExecutor;
    private final java.util.concurrent.Executor externalApiExecutor;
    
    // Tracks in-flight OHLC fetches to prevent cache stampedes
    private final java.util.concurrent.ConcurrentHashMap<String, CompletableFuture<MarketData>> inFlightRequests = new java.util.concurrent.ConcurrentHashMap<>();

    // Tracks in-flight Historical Chart fetches to prevent cache stampedes
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<com.portfolio.marketdata.model.HistoricalData>> inFlightChartRequests = new java.util.concurrent.ConcurrentHashMap<>();

    public MarketDataService(
            MarketDataApiClient marketDataApiClient,
            @org.springframework.lang.Nullable com.portfolio.redis.service.PortfolioMarketDataRedisService marketDataRedisService,
            @org.springframework.lang.Nullable com.am.common.amcommondata.service.price.StockPriceMongoService stockPriceMongoService,
            @org.springframework.lang.Nullable com.am.common.amcommondata.service.price.StockPriceHistoryMongoService stockPriceHistoryMongoService,
            @org.springframework.beans.factory.annotation.Qualifier("taskExecutor") java.util.concurrent.Executor taskExecutor,
            @org.springframework.beans.factory.annotation.Qualifier("externalApiExecutor") java.util.concurrent.Executor externalApiExecutor) {
        this.marketDataApiClient = marketDataApiClient;
        this.marketDataRedisService = marketDataRedisService;
        this.stockPriceMongoService = stockPriceMongoService;
        this.stockPriceHistoryMongoService = stockPriceHistoryMongoService;
        this.taskExecutor = taskExecutor;
        this.externalApiExecutor = externalApiExecutor;
    }

    // Constants
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 15);

    // --- NEW HELPER: Find last valid trading day start epoch ---
    private long findLastTradingDayStartEpoch() {
        ZonedDateTime now = ZonedDateTime.now(IST);
        // If before market open today, use yesterday
        if (now.toLocalTime().isBefore(MARKET_OPEN)) now = now.minusDays(1);
        // Roll back over weekends
        while (now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY) {
            now = now.minusDays(1);
        }
        return now.with(MARKET_OPEN).toInstant().toEpochMilli();
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
            }, externalApiExecutor))
            .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .orTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .exceptionally(e -> null)
            .join();
        Map<String, MarketData> merged = new java.util.HashMap<>();
        futures.stream()
            .filter(f -> !f.isCompletedExceptionally())
            .forEach(f -> merged.putAll(f.getNow(java.util.Collections.emptyMap())));
        
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
        String cleaned = symbol;
        if (colonIndex > 0 && colonIndex < symbol.length() - 1) {
            cleaned = symbol.substring(colonIndex + 1);
        }

        return SymbolResolver.normalize(cleaned);
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

        final int CHUNK_SIZE = 20;
        List<List<String>> chunks = new java.util.ArrayList<>();
        for (int i = 0; i < validSymbols.size(); i += CHUNK_SIZE) {
            chunks.add(validSymbols.subList(i, Math.min(i + CHUNK_SIZE, validSymbols.size())));
        }

        List<java.util.concurrent.CompletableFuture<Map<String, MarketData>>> futures = chunks.stream()
            .map(chunk -> java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    MarketDataResponseWrapper w = marketDataApiClient
                            .getOhlcData(chunk, timeFrame, refresh).block();
                    if (w != null) {
                        return convertToMarketDataMap(w, true);
                    }
                    return Collections.<String, MarketData>emptyMap();
                } catch (Exception e) {
                    log.warn("[OHLC data] API call failed for chunk of {}: {}", chunk.size(), e.getMessage());
                    return Collections.<String, MarketData>emptyMap();
                }
            }, externalApiExecutor))
            .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .orTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .exceptionally(e -> null)
            .join();

        Map<String, MarketData> merged = new java.util.HashMap<>();
        futures.stream()
            .filter(f -> !f.isCompletedExceptionally())
            .forEach(f -> merged.putAll(f.getNow(Collections.emptyMap())));
        
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
        Map<String, MarketData> data = getMarketData(symbols);
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

        // 1.5. Find missing after L1 cache
        List<String> missing = normalized.stream()
            .filter(s -> !result.containsKey(s))
            .collect(Collectors.toList());

        if (missing.isEmpty()) {
            return result;
        }

        // 2. Try market data cache next (populated by am-market service or last live fetch)
        if (marketDataRedisService != null) {
            try {
                Map<String, MarketData> cached = marketDataRedisService.getMarketData(missing);
                if (cached != null) {
                    cached.forEach((symbol, md) -> {
                        boolean hasUsableData = md.getPreviousClose() != null && md.getPreviousClose() > 0
                            || (md.getOhlc() != null && md.getOhlc().getOpen() > 0);
                        if (hasUsableData) {
                            result.put(symbol, md);
                            localCache.put(symbol, md); // promote to L1
                        } else {
                            log.debug("[MarketData] Skipping stale Redis entry for {} due to missing previousClose/openPrice", symbol);
                        }
                    });
                }
            } catch (Exception e) {
                log.warn("[MarketData] Cache read failed: {}", e.getMessage());
            }
            
            missing = normalized.stream()
                .filter(s -> !result.containsKey(s))
                .collect(Collectors.toList());
        }

        if (missing.isEmpty()) {
            return result;
        }

        // 3. Try MongoDB cache next (populated by Kafka live stream)
        if (stockPriceMongoService != null) {
            try {
                java.util.Map<String, com.am.common.amcommondata.document.price.StockPriceDocument> mongoPrices = stockPriceMongoService.getPrices(missing);
                if (mongoPrices != null && !mongoPrices.isEmpty()) {
                    for (java.util.Map.Entry<String, com.am.common.amcommondata.document.price.StockPriceDocument> entry : mongoPrices.entrySet()) {
                        com.am.common.amcommondata.document.price.StockPriceDocument doc = entry.getValue();
                        
                        // Reject stale MongoDB entries — force them through to OHLC API
                        if (doc.getLastPrice() == null || doc.getLastPrice() <= 0) {
                            log.debug("[MarketData] Skipping stale MongoDB entry for {} (lastPrice={})",
                                      doc.getSymbol(), doc.getLastPrice());
                            continue;
                        }
                        // Use lastPrice as same-day previousClose fallback (safe for live intraday data)
                        Double previousClose = (doc.getPreviousClose() != null && doc.getPreviousClose() > 0)
                            ? doc.getPreviousClose()
                            : doc.getLastPrice();

                        MarketData md = MarketData.builder()
                            .symbol(doc.getSymbol())
                            .lastPrice(doc.getLastPrice())
                            .previousClose(previousClose)
                            .timestamp(java.time.Instant.ofEpochMilli(doc.getTimestamp() != null ? doc.getTimestamp() : System.currentTimeMillis()))
                            .ohlc(doc.getOpenPrice() != null && doc.getOpenPrice() > 0 
                                ? com.portfolio.model.market.OhlcData.builder()
                                    .open(doc.getOpenPrice())
                                    .high(doc.getHighPrice())
                                    .low(doc.getLowPrice())
                                    .close(doc.getPreviousClose() != null && doc.getPreviousClose() > 0 ? doc.getPreviousClose() : (doc.getOpenPrice() != null && doc.getOpenPrice() > 0 ? doc.getOpenPrice() : 0.0))
                                    .build()
                                : null)
                            .build();
                        result.put(doc.getSymbol(), md);
                    }
                    log.info("[MarketData] MongoDB cache hit for {} symbols.", mongoPrices.size());
                }
            } catch (Exception e) {
                log.warn("[MarketData] MongoDB read failed: {}", e.getMessage());
            }
            
            missing = normalized.stream()
                .filter(s -> !result.containsKey(s))
                .collect(Collectors.toList());
        }

        if (missing.isEmpty()) {
            log.info("[MarketData] All {} symbols served from caches.", result.size());
            return result;
        }

        // 4. Fetch missing from OHLC API with in-flight deduplication to prevent cache stampedes
        if (!missing.isEmpty()) {
            List<String> toFetch = new java.util.ArrayList<>();
            Map<String, CompletableFuture<MarketData>> waitFor = new HashMap<>();

            for (String symbol : missing) {
                boolean[] isNew = {false};
                CompletableFuture<MarketData> fut = inFlightRequests.computeIfAbsent(symbol, k -> {
                    isNew[0] = true;
                    return new CompletableFuture<>();
                });
                
                if (isNew[0]) {
                    toFetch.add(symbol);
                } else {
                    waitFor.put(symbol, fut); // coalesce with in-flight request
                }
            }

            // Fetch genuinely missing symbols
            if (!toFetch.isEmpty()) {
                log.info("[MarketData] Cache miss for {} symbols. Fetching from API.", toFetch.size());
                
                Map<String, CompletableFuture<MarketData>> newFutures = new HashMap<>();
                for (String s : toFetch) {
                    newFutures.put(s, inFlightRequests.get(s));
                }
                
                try {
                    Map<String, MarketData> fetched = getOhlcData(toFetch, false);
                    if (fetched != null && !fetched.isEmpty()) {
                        fetched.forEach((k, v) -> {
                            result.put(k, v);
                            CompletableFuture<MarketData> fut = newFutures.get(k);
                            if (fut != null) fut.complete(v);
                        });
                        
                        // Store in Redis
                        if (marketDataRedisService != null) {
                            marketDataRedisService.cacheMarketData(fetched);
                        }
                        
                        // Store in MongoDB asynchronously
                        if (stockPriceMongoService != null) {
                            final java.util.Map<String, MarketData> finalFetched = fetched;
                            java.util.concurrent.CompletableFuture.runAsync(() -> {
                                try {
                                    List<com.am.common.amcommondata.document.price.StockPriceDocument> docs = finalFetched.values().stream()
                                        .filter(md -> md != null && md.getLastPrice() != null)
                                        .map(md -> com.am.common.amcommondata.document.price.StockPriceDocument.builder()
                                            .symbol(md.getSymbol())
                                            .lastPrice(md.getLastPrice())
                                            .previousClose(md.getPreviousClose())
                                            .openPrice(md.getOhlc() != null ? md.getOhlc().getOpen() : null)
                                            .highPrice(md.getOhlc() != null ? md.getOhlc().getHigh() : null)
                                            .lowPrice(md.getOhlc() != null ? md.getOhlc().getLow() : null)
                                            .timestamp(md.getTimestamp() != null ? md.getTimestamp().toEpochMilli() : System.currentTimeMillis())
                                            .updatedAt(java.time.LocalDateTime.now())
                                            .build())
                                        .collect(Collectors.toList());
                                    stockPriceMongoService.saveAll(docs);
                                } catch (Exception e) {
                                    log.error("[MarketData] MongoDB save failed", e);
                                }
                            }, taskExecutor);
                        }
                    }
                    
                    // Complete any futures where data wasn't returned
                    newFutures.forEach((k, fut) -> {
                        if (!fut.isDone()) {
                            fut.complete(null);
                        }
                    });
                } catch (Exception e) {
                    log.error("[MarketData] OHLC fetch failed: {}", e.getMessage());
                    newFutures.values().forEach(fut -> {
                        if (!fut.isDone()) fut.completeExceptionally(e);
                    });
                } finally {
                    newFutures.keySet().forEach(inFlightRequests::remove);
                }
            }

            // Join coalesced futures
            if (!waitFor.isEmpty()) {
                CompletableFuture<Void> allWaiting = CompletableFuture.allOf(
                    waitFor.values().toArray(new CompletableFuture[0])
                );
                try {
                    allWaiting.get(90, java.util.concurrent.TimeUnit.SECONDS);
                } catch (java.util.concurrent.TimeoutException e) {
                    log.warn("[InFlight] Coalesced futures timed out at 90s");
                } catch (Exception e) {
                    log.warn("[InFlight] Error waiting for coalesced futures", e);
                }
                waitFor.forEach((symbol, fut) -> {
                    if (fut.isDone() && !fut.isCompletedExceptionally()) {
                        try {
                            MarketData md = fut.get();
                            if (md != null) {
                                result.put(symbol, md);
                            }
                        } catch (Exception ignored) {}
                    }
                });
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

        try {
            return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    Map<String, String> cleanToRaw = new HashMap<>();
                    List<String> cleanedSymbols = new java.util.ArrayList<>();
                    for (String s : symbols) {
                        String clean = cleanSymbol(s);
                        cleanToRaw.put(clean, s);
                        cleanedSymbols.add(clean);
                    }

                    com.portfolio.marketdata.model.BatchSearchRequest request = com.portfolio.marketdata.model.BatchSearchRequest
                            .builder()
                            .queries(cleanedSymbols)
                            .limit(1)
                            .minMatchScore(0.9)
                            .build();

                    com.portfolio.marketdata.model.BatchSearchResponse response = marketDataApiClient.batchSearch(request)
                            .block();

                    if (response == null || response.getResults() == null) {
                        return Collections.<String, com.portfolio.marketdata.model.BatchSearchResponse.SecurityMatch>emptyMap();
                    }

                    Map<String, com.portfolio.marketdata.model.BatchSearchResponse.SecurityMatch> result = new HashMap<>();
                    for (com.portfolio.marketdata.model.BatchSearchResponse.QueryResult qr : response.getResults()) {
                        if (qr.getMatches() != null && !qr.getMatches().isEmpty()) {
                            String rawSymbol = cleanToRaw.getOrDefault(qr.getQuery(), qr.getQuery());
                            result.put(rawSymbol, qr.getMatches().get(0));
                        }
                    }
                    return result;
                } catch (Exception e) {
                    log.error("[MarketCap data] Error fetching market cap data for {} symbols: {}", symbols.size(), e.getMessage());
                    return Collections.<String, com.portfolio.marketdata.model.BatchSearchResponse.SecurityMatch>emptyMap();
                }
            }, taskExecutor)
            .orTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .exceptionally(e -> {
                log.warn("[MarketCap data] Fetch timed out or failed: {}", e.getMessage());
                return Collections.<String, com.portfolio.marketdata.model.BatchSearchResponse.SecurityMatch>emptyMap();
            })
            .join();
        } catch (Exception e) {
            log.warn("[MarketCap data] Unexpected error during fetch: {}", e.getMessage());
            return Collections.emptyMap();
        }
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

        com.portfolio.marketdata.model.HistoricalChartsResponse finalResp = new com.portfolio.marketdata.model.HistoricalChartsResponse();
        finalResp.setData(new java.util.HashMap<>());
        
        Map<String, String> cleanToRaw = new java.util.HashMap<>();
        List<String> missingSymbols = new java.util.ArrayList<>();
        List<String> toFetch = new java.util.ArrayList<>();
        Map<String, java.util.concurrent.CompletableFuture<com.portfolio.marketdata.model.HistoricalData>> waitFor = new java.util.HashMap<>();
        
        for (String symbol : validSymbols) {
            String cleanSym = cleanSymbol(symbol);
            cleanToRaw.put(cleanSym, symbol);
            
            String cacheKey = cleanSym + ":" + range;
            com.portfolio.marketdata.model.HistoricalData cachedData = chartCache.getIfPresent(cacheKey);
            if (cachedData != null) {
                finalResp.getData().put(symbol, cachedData);
            } else {
                if (!missingSymbols.contains(cleanSym)) {
                    missingSymbols.add(cleanSym);
                    boolean[] isNew = {false};
                    java.util.concurrent.CompletableFuture<com.portfolio.marketdata.model.HistoricalData> fut = inFlightChartRequests.computeIfAbsent(cacheKey, k -> {
                        isNew[0] = true;
                        return new java.util.concurrent.CompletableFuture<>();
                    });
                    
                    if (isNew[0]) {
                        toFetch.add(cleanSym);
                    } else {
                        waitFor.put(cleanSym, fut); // coalesce with in-flight request
                    }
                }
            }
        }
        
        // Wait for any in-flight deduplicated requests to finish
        if (!waitFor.isEmpty()) {
            java.util.concurrent.CompletableFuture.allOf(waitFor.values().toArray(new java.util.concurrent.CompletableFuture[0]))
                .orTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
                .exceptionally(e -> null)
                .join();
            for (Map.Entry<String, java.util.concurrent.CompletableFuture<com.portfolio.marketdata.model.HistoricalData>> entry : waitFor.entrySet()) {
                String cleanSym = entry.getKey();
                String rawSym = cleanToRaw.get(cleanSym);
                try {
                    com.portfolio.marketdata.model.HistoricalData hd = entry.getValue().getNow(null);
                    if (hd != null) {
                        finalResp.getData().put(rawSym, hd);
                    }
                } catch (Exception e) {
                    log.warn("[HistoricalCharts data] Waiting on future failed: {}", e.getMessage());
                }
            }
        }

        if (toFetch.isEmpty()) {
            log.info("Served {} historical charts from L1 cache / coalesced requests for range={}", validSymbols.size(), range);
            return finalResp;
        }

        log.info("Getting historical charts for {} missing symbols with range={}", toFetch.size(), range);

        com.portfolio.marketdata.model.HistoricalChartsResponse fetchedResp = new com.portfolio.marketdata.model.HistoricalChartsResponse();
        fetchedResp.setData(new java.util.HashMap<>());

        if ("1D".equalsIgnoreCase(range) && stockPriceHistoryMongoService != null) {
            buildIntradayFromMongo(toFetch, fetchedResp, cleanToRaw);
        } else {
            callExternalApiForRange(toFetch, range, fetchedResp, cleanToRaw);
        }

        // Add fetched data to finalResp and complete the futures for any waiting threads
        for (String cleanSym : toFetch) {
            String rawSym = cleanToRaw.get(cleanSym);
            com.portfolio.marketdata.model.HistoricalData hd = null;
            if (fetchedResp != null && fetchedResp.getData() != null) {
                hd = fetchedResp.getData().get(rawSym);
                if (hd != null) {
                    finalResp.getData().put(rawSym, hd);
                }
            }
            java.util.concurrent.CompletableFuture<com.portfolio.marketdata.model.HistoricalData> fut = inFlightChartRequests.remove(cleanSym + ":" + range);
            if (fut != null) {
                fut.complete(hd);
            }
        }

        return finalResp;
    }

    private com.portfolio.marketdata.model.HistoricalChartsResponse callExternalApiForRange(
            List<String> missingSymbols, String range, 
            com.portfolio.marketdata.model.HistoricalChartsResponse finalResp,
            Map<String, String> cleanToRaw) {
        try {
            int CHUNK_SIZE = 20;
            List<List<String>> chunks = new java.util.ArrayList<>();
            for (int i = 0; i < missingSymbols.size(); i += CHUNK_SIZE) {
                chunks.add(missingSymbols.subList(i, Math.min(missingSymbols.size(), i + CHUNK_SIZE)));
            }

            List<java.util.concurrent.CompletableFuture<com.portfolio.marketdata.model.HistoricalChartsResponse>> futures = chunks.stream()
                .map(chunk -> java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        return marketDataApiClient.getHistoricalCharts(chunk, range).block();
                    } catch (Exception e) {
                        log.error("[HistoricalCharts data] API call failed: {}", e.getMessage());
                        com.portfolio.marketdata.model.HistoricalChartsResponse resp = new com.portfolio.marketdata.model.HistoricalChartsResponse();
                        resp.setData(new java.util.HashMap<>());
                        return resp;
                    }
                }, taskExecutor)
                .orTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .exceptionally(e -> {
                    log.warn("[HistoricalCharts data] Fetch timed out or failed: {}", e.getMessage());
                    com.portfolio.marketdata.model.HistoricalChartsResponse resp = new com.portfolio.marketdata.model.HistoricalChartsResponse();
                    resp.setData(new java.util.HashMap<>());
                    return resp;
                }))
                .collect(Collectors.toList());

            java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
            
            for (java.util.concurrent.CompletableFuture<com.portfolio.marketdata.model.HistoricalChartsResponse> f : futures) {
                com.portfolio.marketdata.model.HistoricalChartsResponse chunkResp = f.join();
                if (chunkResp != null && chunkResp.getData() != null) {
                    chunkResp.getData().forEach((cleanSym, data) -> {
                        String rawSym = cleanToRaw.getOrDefault(cleanSym, cleanSym);
                        finalResp.getData().put(rawSym, data);
                        chartCache.put(cleanSym + ":" + range, data);
                    });
                }
            }
            return finalResp;
        } catch (Exception e) {
            log.warn("[HistoricalCharts data] Unexpected error during fetch: {}", e.getMessage());
            return finalResp;
        }
    }

    private com.portfolio.marketdata.model.HistoricalChartsResponse buildIntradayFromMongo(
            List<String> symbols, com.portfolio.marketdata.model.HistoricalChartsResponse partial, Map<String, String> cleanToRaw) {
        
        long startEpoch;
        try {
            startEpoch = findLastTradingDayStartEpoch();
        } catch (Exception e) {
            log.error("[Intraday-Mongo] Date resolution failed, falling back to API", e);
            return callExternalApiForRange(symbols, "1D", partial, cleanToRaw);
        }

        Map<String, List<StockPriceHistoryDocument>> historyBySymbol = new HashMap<>();
        try {
            historyBySymbol = stockPriceHistoryMongoService.getIntradayHistory(symbols, startEpoch);
        } catch (Exception e) {
            log.error("[Intraday-Mongo] DB query failed, falling back to API for all symbols", e);
            return callExternalApiForRange(symbols, "1D", partial, cleanToRaw);
        }

        List<String> fallbackSymbols = new ArrayList<>();
        Map<String, Double> liveSnapshots = new HashMap<>();

        for (String cleanSymbol : symbols) {
            List<StockPriceHistoryDocument> ticks = historyBySymbol.getOrDefault(cleanSymbol, List.of());

            if (ticks.isEmpty()) {
                // No history in DB for this symbol — schedule it for fallback
                fallbackSymbols.add(cleanSymbol);
                continue;
            }

            // Convert MongoDB ticks → OHLCVTPoint (IST timestamps, price fills OHLC)
            List<OHLCVTPoint> points = ticks.stream().map(tick -> {
                LocalDateTime ldt = Instant.ofEpochMilli(tick.getTimestampMinute())
                    .atZone(IST).toLocalDateTime();
                return OHLCVTPoint.builder()
                    .time(ldt)
                    .open(tick.getPrice())
                    .high(tick.getPrice())
                    .low(tick.getPrice())
                    .close(tick.getPrice())
                    .volume(0L)
                    .build();
            }).collect(Collectors.toList());

            com.portfolio.marketdata.model.HistoricalData hd = com.portfolio.marketdata.model.HistoricalData.builder()
                .tradingSymbol(cleanSymbol)
                .interval("1min")
                .dataPoints(points)
                .dataPointCount(points.size())
                .build();

            String rawSymbol = cleanToRaw.getOrDefault(cleanSymbol, cleanSymbol);
            partial.getData().put(rawSymbol, hd);
            chartCache.put(cleanSymbol + ":1D", hd); // Populate L1 cache for next call
        }

        // ── FALLBACK: Symbols missing from MongoDB ──
        if (!fallbackSymbols.isEmpty()) {
            log.warn("[Intraday-Mongo] {} symbols missing from DB, falling back to API individually: {}",
                fallbackSymbols.size(), fallbackSymbols);
            
            try {
                Map<String, StockPriceDocument> liveDocs = 
                    stockPriceMongoService != null ? 
                    stockPriceMongoService.getPrices(fallbackSymbols) : Map.of();

                // Call external API for the missing symbols
                com.portfolio.marketdata.model.HistoricalChartsResponse apiResp = 
                    callExternalApiForRange(fallbackSymbols, "1D", new com.portfolio.marketdata.model.HistoricalChartsResponse(), cleanToRaw);
                
                for (String cleanSymbol : fallbackSymbols) {
                    String rawSymbol = cleanToRaw.getOrDefault(cleanSymbol, cleanSymbol);
                    com.portfolio.marketdata.model.HistoricalData apiData = (apiResp != null && apiResp.getData() != null)
                        ? apiResp.getData().get(rawSymbol) : null;
                    
                    boolean apiDataValid = apiData != null 
                        && apiData.getDataPoints() != null 
                        && !apiData.getDataPoints().isEmpty()
                        && apiData.getDataPoints().stream().anyMatch(p -> p.getClose() != null && p.getClose() > 0);

                    if (apiDataValid) {
                        // API returned good data (callExternalApiForRange already put it in partial under rawSymbol if we passed partial, 
                        // but here we passed a new response, so we extract and put it manually)
                        partial.getData().put(rawSymbol, apiData);
                        chartCache.put(cleanSymbol + ":1D", apiData);
                    } else {
                        // API also returned null/0.0 → create a flat-line chart using live price
                        StockPriceDocument liveDoc = liveDocs.get(cleanSymbol);
                        if (liveDoc != null && liveDoc.getLastPrice() != null && liveDoc.getLastPrice() > 0) {
                            log.warn("[Intraday-Mongo] Symbol {} has no OHLC data, using flat live price: {}", 
                                cleanSymbol, liveDoc.getLastPrice());
                            com.portfolio.marketdata.model.HistoricalData flatLine = buildFlatLineChart(cleanSymbol, liveDoc.getLastPrice());
                            partial.getData().put(rawSymbol, flatLine);
                            chartCache.put(cleanSymbol + ":1D", flatLine);
                        } else {
                            log.warn("[Intraday-Mongo] Symbol {} has no data at all, omitting from chart.", cleanSymbol);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[Intraday-Mongo] Fallback API call failed: {}", e.getMessage());
            }
        }

        return partial;
    }

    /** Creates a flat-line chart using a fixed price for the entire trading session. */
    private com.portfolio.marketdata.model.HistoricalData buildFlatLineChart(String symbol, double price) {
        List<OHLCVTPoint> points = new ArrayList<>();
        LocalDate today = LocalDate.now(IST);
        LocalDateTime t = LocalDateTime.of(today, MARKET_OPEN);
        LocalDateTime end = LocalDateTime.of(today, LocalTime.of(15, 30));
        while (!t.isAfter(end)) {
            points.add(OHLCVTPoint.builder().time(t).open(price).high(price).low(price).close(price).volume(0L).build());
            t = t.plusMinutes(1);
        }
        return com.portfolio.marketdata.model.HistoricalData.builder().tradingSymbol(symbol).interval("1min").dataPoints(points).dataPointCount(points.size()).build();
    }
}
