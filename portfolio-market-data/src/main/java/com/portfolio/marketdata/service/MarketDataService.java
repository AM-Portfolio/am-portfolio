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

    /** Prior-session closes for closed-market day% (avoid historical on every holdings hit). */
    private final com.github.benmanes.caffeine.cache.Cache<String, Double> priorCloseCache =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
            .expireAfterWrite(18, java.util.concurrent.TimeUnit.HOURS)
            .maximumSize(20000)
            .build();

    /**
     * Symbols where prior-close hist was already attempted this TTL window.
     * Kept separate from {@link #priorCloseCache} so a miss never poisons day% with last≈prev.
     */
    private final com.github.benmanes.caffeine.cache.Cache<String, Boolean> priorClosePrefetchAttempted =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
            .expireAfterWrite(18, java.util.concurrent.TimeUnit.HOURS)
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

    @org.springframework.lang.Nullable
    private final com.portfolio.marketdata.config.MarketDataApiConfig config;

    public MarketDataService(
            MarketDataApiClient marketDataApiClient,
            @org.springframework.lang.Nullable com.portfolio.redis.service.PortfolioMarketDataRedisService marketDataRedisService,
            @org.springframework.lang.Nullable com.am.common.amcommondata.service.price.StockPriceMongoService stockPriceMongoService,
            @org.springframework.lang.Nullable com.am.common.amcommondata.service.price.StockPriceHistoryMongoService stockPriceHistoryMongoService,
            @org.springframework.beans.factory.annotation.Qualifier("taskExecutor") java.util.concurrent.Executor taskExecutor,
            @org.springframework.beans.factory.annotation.Qualifier("externalApiExecutor") java.util.concurrent.Executor externalApiExecutor) {
        this(marketDataApiClient, marketDataRedisService, stockPriceMongoService, stockPriceHistoryMongoService, taskExecutor, externalApiExecutor, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MarketDataService(
            MarketDataApiClient marketDataApiClient,
            @org.springframework.lang.Nullable com.portfolio.redis.service.PortfolioMarketDataRedisService marketDataRedisService,
            @org.springframework.lang.Nullable com.am.common.amcommondata.service.price.StockPriceMongoService stockPriceMongoService,
            @org.springframework.lang.Nullable com.am.common.amcommondata.service.price.StockPriceHistoryMongoService stockPriceHistoryMongoService,
            @org.springframework.beans.factory.annotation.Qualifier("taskExecutor") java.util.concurrent.Executor taskExecutor,
            @org.springframework.beans.factory.annotation.Qualifier("externalApiExecutor") java.util.concurrent.Executor externalApiExecutor,
            @org.springframework.lang.Nullable com.portfolio.marketdata.config.MarketDataApiConfig config) {
        this.marketDataApiClient = marketDataApiClient;
        this.marketDataRedisService = marketDataRedisService;
        this.stockPriceMongoService = stockPriceMongoService;
        this.stockPriceHistoryMongoService = stockPriceHistoryMongoService;
        this.taskExecutor = taskExecutor;
        this.externalApiExecutor = externalApiExecutor;
        this.config = config;
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
     * Partition symbols according to configurable batch settings (single vs parallel chunking).
     */
    private List<List<String>> partitionSymbols(List<String> symbols) {
        int chunkSize = (config != null && config.getBatch() != null && config.getBatch().getChunkSize() > 0) ? config.getBatch().getChunkSize() : 20;
        boolean parallel = (config != null && config.getBatch() != null) && config.getBatch().isParallelBatchingEnabled();

        if (!parallel || symbols.size() <= chunkSize) {
            log.debug("[MarketData] Single-batch mode: sending {} symbols in 1 request (chunkSize={})", symbols.size(), chunkSize);
            return Collections.singletonList(symbols);
        }

        log.info("[MarketData] Parallel-batch mode: splitting {} symbols into chunks of {}", symbols.size(), chunkSize);
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < symbols.size(); i += chunkSize) {
            chunks.add(symbols.subList(i, Math.min(i + chunkSize, symbols.size())));
        }
        return chunks;
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

        // A2: prefer fewer larger START_END batches (cold 1Y SLO); wave still bounded.
        List<List<String>> chunks = new ArrayList<>();
        final int histChunkSize = 50;
        for (int i = 0; i < validSymbols.size(); i += histChunkSize) {
            chunks.add(validSymbols.subList(i, Math.min(i + histChunkSize, validSymbols.size())));
        }
        return fetchHistoricalChunks(chunks, request);
    }

    /**
     * Bounded parallel hist waves so market-data stays healthy without serializing 12× timeouts.
     */
    private Map<String, MarketData> fetchHistoricalChunks(
            List<List<String>> chunks, HistoricalDataRequest request) {
        Map<String, MarketData> merged = new HashMap<>();
        if (chunks.isEmpty()) {
            return merged;
        }

        int maxParallel = 4;
        int readTimeoutMs = (config != null && config.getReadTimeout() > 0) ? config.getReadTimeout() : 45_000;
        // Wave budget must exceed WebClient read timeout so we never getNow(empty) on in-flight OK work.
        long waveTimeoutMs = Math.max(readTimeoutMs + 5_000L, 50_000L);

        for (int i = 0; i < chunks.size(); i += maxParallel) {
            List<List<String>> wave = chunks.subList(i, Math.min(i + maxParallel, chunks.size()));
            List<CompletableFuture<Map<String, MarketData>>> futures = wave.stream()
                    .map(chunk -> CompletableFuture.supplyAsync(() -> {
                        try {
                            HistoricalDataRequest chunkRequest = HistoricalDataRequest.builder()
                                    .symbols(String.join(",", chunk))
                                    .fromDate(request.getFromDate())
                                    .toDate(request.getToDate())
                                    .interval(request.getInterval())
                                    .filterType(request.getFilterType())
                                    .instrumentType(request.getInstrumentType())
                                    .isIndexSymbol(request.getIsIndexSymbol())
                                    .filterFrequency(request.getFilterFrequency())
                                    .continuous(request.getContinuous())
                                    .forceRefresh(request.getForceRefresh())
                                    .additionalParams(request.getAdditionalParams())
                                    .build();

                            HistoricalDataResponseWrapper response =
                                    marketDataApiClient.getHistoricalData(chunkRequest).block();
                            if (response == null || response.getData() == null) {
                                log.warn("[HistoricalData chunk] null/empty wrapper for {} symbols", chunk.size());
                                return Collections.<String, MarketData>emptyMap();
                            }
                            Map<String, MarketData> chunkResult = new HashMap<>();
                            int skipped = 0;
                            for (Map.Entry<String, HistoricalDataResponse> entry : response.getData().entrySet()) {
                                HistoricalDataResponse raw = entry.getValue();
                                MarketData converted =
                                        MarketDataConverter.fromHistoricalDataResponse(raw);
                                if (converted != null) {
                                    chunkResult.put(entry.getKey(), converted);
                                } else {
                                    skipped++;
                                    if (skipped <= 2) {
                                        int pts = raw == null ? -1 : raw.effectiveDataPoints().size();
                                        log.warn(
                                                "[HistoricalData chunk] convert null for {} (rawNull={}, pts={})",
                                                entry.getKey(),
                                                raw == null,
                                                pts);
                                    }
                                }
                            }
                            if (chunkResult.isEmpty() && !response.getData().isEmpty()) {
                                log.warn(
                                        "[HistoricalData chunk] API returned {} symbols but 0 converted",
                                        response.getData().size());
                            }
                            return chunkResult;
                        } catch (Exception e) {
                            log.warn("[HistoricalData chunk] Failed for chunk of {}: {}", chunk.size(), e.getMessage());
                            return Collections.<String, MarketData>emptyMap();
                        }
                    }, externalApiExecutor))
                    .collect(Collectors.toList());

            long deadlineNanos = System.nanoTime()
                    + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(waveTimeoutMs);
            int incomplete = 0;
            int convertedThisWave = 0;
            for (CompletableFuture<Map<String, MarketData>> future : futures) {
                long remainingMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                        deadlineNanos - System.nanoTime());
                if (remainingMs <= 0) {
                    incomplete++;
                    future.cancel(true);
                    continue;
                }
                try {
                    Map<String, MarketData> part = future.get(
                            remainingMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (part != null && !part.isEmpty()) {
                        merged.putAll(part);
                        convertedThisWave += part.size();
                    }
                } catch (java.util.concurrent.TimeoutException te) {
                    incomplete++;
                    future.cancel(true);
                    log.warn("[HistoricalData] Chunk still incomplete after wave budget — cancelled");
                } catch (Exception e) {
                    incomplete++;
                    log.warn("[HistoricalData] Chunk join failed: {}", e.getMessage());
                }
            }
            log.info(
                    "[HistoricalData] Wave done: converted={} incomplete={} waveSize={}",
                    convertedThisWave,
                    incomplete,
                    wave.size());
        }

        log.info("[HistoricalData] Merged {}/{} symbols across {} chunk(s) (maxParallel={})",
                merged.size(),
                chunks.stream().mapToInt(List::size).sum(),
                chunks.size(),
                maxParallel);
        return merged;
    }

    /**
     * Cleans a symbol by removing exchange prefixes like NSE:, BSE:, etc.
     * 
     * @param symbol The symbol with potential exchange prefix
     * @return The cleaned symbol without the exchange prefix
     */
    private boolean hasUsablePrice(MarketData md) {
        if (md == null) {
            return false;
        }
        if (md.getLastPrice() != null && md.getLastPrice() > 0) {
            return true;
        }
        if (md.getPreviousClose() != null && md.getPreviousClose() > 0) {
            return true;
        }
        return md.getOhlc() != null
                && ((md.getOhlc().getClose() > 0) || (md.getOhlc().getOpen() > 0));
    }

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
     * Splits large symbol lists into bounded parallel chunks to avoid Upstox stampedes.
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

        List<List<String>> chunks = partitionSymbols(validSymbols);
        return fetchOhlcChunks(chunks, timeFrame, refresh);
    }

    /**
     * Fetch OHLC chunks with a bounded concurrency wave so one 100+ symbol portfolio
     * cannot open unlimited parallel Upstox-backed calls.
     */
    private Map<String, MarketData> fetchOhlcChunks(List<List<String>> chunks, String timeFrame, boolean refresh) {
        Map<String, MarketData> merged = new HashMap<>();
        if (chunks.isEmpty()) {
            return merged;
        }

        int readTimeoutMs = (config != null && config.getReadTimeout() > 0) ? config.getReadTimeout() : 32000;
        int maxParallel = 2;
        if (config != null && config.getBatch() != null && config.getBatch().getMaxParallelChunks() > 0) {
            maxParallel = config.getBatch().getMaxParallelChunks();
        }
        // Wave timeout = client timeout + small buffer; never the old 60s/90s hang.
        long waveTimeoutMs = Math.max(readTimeoutMs + 1500L, 5000L);

        for (int i = 0; i < chunks.size(); i += maxParallel) {
            List<List<String>> wave = chunks.subList(i, Math.min(i + maxParallel, chunks.size()));
            List<CompletableFuture<Map<String, MarketData>>> futures = wave.stream()
                    .map(chunk -> CompletableFuture.supplyAsync(() -> {
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

            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .orTimeout(waveTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .exceptionally(e -> null)
                        .join();
            } catch (Exception e) {
                log.warn("[OHLC data] Wave join failed after {}ms: {}", waveTimeoutMs, e.getMessage());
            }

            for (CompletableFuture<Map<String, MarketData>> future : futures) {
                if (!future.isCompletedExceptionally()) {
                    merged.putAll(future.getNow(Collections.emptyMap()));
                }
            }
        }

        log.info("[OHLC data] Merged {}/{} symbols across {} chunk(s) (maxParallel={})",
                merged.size(),
                chunks.stream().mapToInt(List::size).sum(),
                chunks.size(),
                maxParallel);
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
        Map<String, Double> prices = new HashMap<>();
        for (Map.Entry<String, MarketData> e : data.entrySet()) {
            Double px = resolveUsableLastPrice(e.getValue());
            if (px != null) {
                prices.put(e.getKey(), px);
            }
        }
        return prices;
    }

    private Double resolveUsableLastPrice(MarketData md) {
        if (md == null) {
            return null;
        }
        if (md.getLastPrice() != null && md.getLastPrice() > 0) {
            return md.getLastPrice();
        }
        if (md.getPreviousClose() != null && md.getPreviousClose() > 0) {
            return md.getPreviousClose();
        }
        if (md.getOhlc() != null) {
            if (md.getOhlc().getClose() > 0) {
                return md.getOhlc().getClose();
            }
            if (md.getOhlc().getOpen() > 0) {
                return md.getOhlc().getOpen();
            }
        }
        return null;
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
        return getMarketData(symbols, false);
    }

    /**
     * @param skipHistRepair when true (e.g. basket Preview), skip after-hours prior-close
     *                       hist repair — Preview tables need LTP/close, not day%.
     */
    public Map<String, MarketData> getMarketData(List<String> symbols, boolean skipHistRepair) {
        if (symbols == null || symbols.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, MarketData> result = new HashMap<>();
        List<String> normalized = symbols.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(this::cleanSymbol)
                .distinct()
                .collect(Collectors.toList());

        // 1. L1 in-pod cache (was written but never read — restore for basket latency)
        Map<String, MarketData> l1Hits = localCache.getAllPresent(normalized);
        if (l1Hits != null && !l1Hits.isEmpty()) {
            result.putAll(l1Hits);
        }

        List<String> missing = normalized.stream()
            .filter(s -> !result.containsKey(s))
            .collect(Collectors.toList());

        boolean cashOpen = marketDataRedisService != null && marketDataRedisService.isCashMarketHours();

        if (missing.isEmpty()) {
            log.debug("[MarketData] All {} symbols served from L1", result.size());
            return finalizeMarketData(result, cashOpen, skipHistRepair);
        }

        // Cash-open: prefer fresh last-trade caches.
        // Cash-closed: still use Redis/Mongo previousClose/lastPrice for basket APIs (avoid OHLC waves),
        // then fall through to OHLC only for true misses.
        if (!cashOpen) {
            log.info("[MarketData] Cash session closed — using Redis/Mongo close prices before OHLC for {} missing",
                    missing.size());
        }

        // 2. Redis last-trade / previous-close cache
        if (marketDataRedisService != null) {
            try {
                Map<String, MarketData> cached = marketDataRedisService.getMarketData(missing);
                if (cached != null) {
                    cached.forEach((symbol, md) -> {
                        boolean hasUsableData = hasUsablePrice(md);
                        if (hasUsableData) {
                            result.put(symbol, md);
                            localCache.put(symbol, md);
                        } else {
                            log.debug("[MarketData] Skipping Redis entry for {} — no usable price", symbol);
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
            return finalizeMarketData(result, cashOpen, skipHistRepair);
        }

        // 3. MongoDB cache (Kafka live stream / prior OHLC writes)
        if (stockPriceMongoService != null) {
            try {
                java.util.Map<String, com.am.common.amcommondata.document.price.StockPriceDocument> mongoPrices =
                        stockPriceMongoService.getPrices(missing);
                if (mongoPrices != null && !mongoPrices.isEmpty()) {
                    for (java.util.Map.Entry<String, com.am.common.amcommondata.document.price.StockPriceDocument> entry : mongoPrices.entrySet()) {
                        com.am.common.amcommondata.document.price.StockPriceDocument doc = entry.getValue();

                        // During cash hours, reject very stale ticks; after hours keep last known close.
                        boolean isStale = cashOpen
                                && doc.getUpdatedAt() != null
                                && doc.getUpdatedAt().isBefore(java.time.LocalDateTime.now().minusHours(6));
                        if (isStale) {
                            log.info("[MarketData] Stale MongoDB entry for {} (updatedAt={}), forcing API refresh",
                                doc.getSymbol(), doc.getUpdatedAt());
                            continue;
                        }

                        Double usable = doc.getLastPrice() != null && doc.getLastPrice() > 0
                                ? doc.getLastPrice()
                                : (doc.getPreviousClose() != null && doc.getPreviousClose() > 0 ? doc.getPreviousClose() : null);
                        if (usable == null) {
                            log.debug("[MarketData] Skipping MongoDB entry for {} (no last/prev close)",
                                      doc.getSymbol());
                            continue;
                        }
                        Double previousClose = (doc.getPreviousClose() != null && doc.getPreviousClose() > 0)
                            ? doc.getPreviousClose()
                            : null;

                        double openPx = (doc.getOpenPrice() != null && doc.getOpenPrice() > 0)
                                ? doc.getOpenPrice()
                                : (previousClose != null ? previousClose : usable);
                        double highPx = (doc.getHighPrice() != null && doc.getHighPrice() > 0)
                                ? doc.getHighPrice() : Math.max(openPx, usable);
                        double lowPx = (doc.getLowPrice() != null && doc.getLowPrice() > 0)
                                ? doc.getLowPrice() : Math.min(openPx, usable);
                        MarketData md = MarketData.builder()
                            .symbol(doc.getSymbol())
                            .lastPrice(usable)
                            .previousClose(previousClose)
                            .timestamp(java.time.Instant.ofEpochMilli(doc.getTimestamp() != null ? doc.getTimestamp() : System.currentTimeMillis()))
                            // Always attach OHLC when LTP exists so live heatmap never drops tick-only symbols.
                            .ohlc(com.portfolio.model.market.OhlcData.builder()
                                    .open(openPx)
                                    .high(highPx)
                                    .low(lowPx)
                                    .close(usable)
                                    .build())
                            .build();
                        result.put(doc.getSymbol(), md);
                        localCache.put(doc.getSymbol(), md);
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
            return finalizeMarketData(result, cashOpen, skipHistRepair);
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
                            localCache.put(k, v);
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
                                            .previousClose((md.getPreviousClose() != null && md.getPreviousClose() > 0) ? md.getPreviousClose() : null)
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
                    allWaiting.get(35, java.util.concurrent.TimeUnit.SECONDS);
                } catch (java.util.concurrent.TimeoutException e) {
                    log.warn("[InFlight] Coalesced futures timed out at 35s");
                } catch (Exception e) {
                    log.warn("[InFlight] Error waiting for coalesced futures", e);
                }
                waitFor.forEach((symbol, fut) -> {
                    if (fut.isDone() && !fut.isCompletedExceptionally()) {
                        try {
                            MarketData md = fut.get();
                            if (md != null) {
                                result.put(symbol, md);
                                localCache.put(symbol, md);
                            }
                        } catch (Exception ignored) {}
                    }
                });
            }
        }



        // ─── Manager Audit: Log all resolution failures ───────────────────────────
        java.util.Set<String> requested = new java.util.HashSet<>(symbols);
        java.util.List<String> noPayload = requested.stream()
            .filter(s -> !result.containsKey(cleanSymbol(s))).sorted().collect(Collectors.toList());
        java.util.List<String> noLtp = result.entrySet().stream()
            .filter(e -> e.getValue().getLastPrice() == null || e.getValue().getLastPrice() <= 0)
            .map(Map.Entry::getKey).sorted().collect(Collectors.toList());
        java.util.List<String> noPrevClose = result.entrySet().stream()
            .filter(e -> e.getValue().getPreviousClose() == null || e.getValue().getPreviousClose() <= 0)
            .map(Map.Entry::getKey).sorted().collect(Collectors.toList());

        if (!noPayload.isEmpty())
            log.warn("[MarketData Audit] No price payload ({}) → {}", noPayload.size(), String.join(",", noPayload));
        if (!noLtp.isEmpty())
            log.warn("[MarketData Audit] Returned but no LTP ({}) → {}", noLtp.size(), String.join(",", noLtp));
        if (!noPrevClose.isEmpty())
            log.warn("[MarketData Audit] Missing previousClose ({}) → {}", noPrevClose.size(), String.join(",", noPrevClose));
        // ─────────────────────────────────────────────────────────────────────────────

        log.info("[MarketData] Result: {}/{} symbols returned.", result.size(), symbols.size());
        return finalizeMarketData(result, cashOpen, skipHistRepair);
    }

    private Map<String, MarketData> finalizeMarketData(Map<String, MarketData> result, boolean cashOpen) {
        return finalizeMarketData(result, cashOpen, false);
    }

    private Map<String, MarketData> finalizeMarketData(
            Map<String, MarketData> result, boolean cashOpen, boolean skipHistRepair) {
        if (!cashOpen && !skipHistRepair) {
            repairCollapsedPreviousClose(result);
        }
        return result;
    }

    /**
     * When session is closed, lastPrice often equals rolled previousClose → day% collapses to ~0.
     * Prefer cached prior closes; historical lookback only for cache misses (once per symbol / 18h).
     * Never store collapsed last≈prev into {@link #priorCloseCache} (use {@link #priorClosePrefetchAttempted}).
     */
    void repairCollapsedPreviousClose(Map<String, MarketData> result) {
        if (result == null || result.isEmpty()) {
            return;
        }
        List<String> needsPrior = new ArrayList<>();
        int fromCache = 0;
        Map<String, MarketData> repairedForRedis = new HashMap<>();
        for (Map.Entry<String, MarketData> e : result.entrySet()) {
            MarketData md = e.getValue();
            if (!needsPriorSessionClose(md)) {
                continue;
            }
            Double cached = priorCloseCache.getIfPresent(e.getKey());
            if (cached != null && cached > 0 && isDistinctPrior(cached, md.getLastPrice())) {
                md.setPreviousClose(cached);
                localCache.put(e.getKey(), md);
                repairedForRedis.put(e.getKey(), md);
                fromCache++;
            } else if (Boolean.TRUE.equals(priorClosePrefetchAttempted.getIfPresent(e.getKey()))) {
                // Already tried hist this window — leave collapsed; do not re-hit API.
                fromCache++;
            } else {
                needsPrior.add(e.getKey());
            }
        }
        if (needsPrior.isEmpty()) {
            if (fromCache > 0) {
                log.debug("[MarketData] Applied {} priorClose values from cache", fromCache);
            }
            writeBackRepairedMarketData(repairedForRedis);
            return;
        }
        try {
            LocalDate to = LocalDate.now(IST);
            LocalDate from = to.minusDays(14);
            HistoricalDataRequest request = HistoricalDataRequest.builder()
                    .symbols(String.join(",", needsPrior))
                    .fromDate(from.toString())
                    .toDate(to.toString())
                    .interval("day")
                    .build();
            Map<String, MarketData> hist = getHistoricalData(request);
            if (hist == null || hist.isEmpty()) {
                for (String symbol : needsPrior) {
                    priorClosePrefetchAttempted.put(symbol, Boolean.TRUE);
                }
                writeBackRepairedMarketData(repairedForRedis);
                return;
            }
            int repaired = 0;
            for (String symbol : needsPrior) {
                MarketData live = result.get(symbol);
                MarketData h = hist.get(symbol);
                if (h == null) {
                    h = hist.get(cleanSymbol(symbol));
                }
                if (live == null) {
                    continue;
                }
                priorClosePrefetchAttempted.put(symbol, Boolean.TRUE);
                Double prior = h != null ? resolvePriorCloseFromHistorical(h, live.getLastPrice()) : null;
                if (prior != null && prior > 0 && isDistinctPrior(prior, live.getLastPrice())) {
                    live.setPreviousClose(prior);
                    priorCloseCache.put(symbol, prior);
                    localCache.put(symbol, live);
                    repairedForRedis.put(symbol, live);
                    repaired++;
                }
                // Miss: mark attempted only — never poison priorCloseCache with last≈prev.
            }
            log.info("[MarketData] Repaired previousClose hist={}/{} cacheHit={}",
                    repaired, needsPrior.size(), fromCache);
            writeBackRepairedMarketData(repairedForRedis);
        } catch (Exception e) {
            log.warn("[MarketData] prior-close repair failed: {}", e.getMessage());
            for (String symbol : needsPrior) {
                priorClosePrefetchAttempted.put(symbol, Boolean.TRUE);
            }
        }
    }

    private void writeBackRepairedMarketData(Map<String, MarketData> repaired) {
        if (repaired == null || repaired.isEmpty() || marketDataRedisService == null) {
            return;
        }
        try {
            marketDataRedisService.cacheMarketData(repaired);
        } catch (Exception e) {
            log.debug("[MarketData] Redis write-back of repaired priorClose skipped: {}", e.getMessage());
        }
    }

    static boolean needsPriorSessionClose(MarketData md) {
        if (md == null) {
            return false;
        }
        Double last = md.getLastPrice();
        if (last == null || last <= 0) {
            return false;
        }
        Double prev = md.getPreviousClose();
        if (prev == null || prev <= 0) {
            return true;
        }
        return !isDistinctPrior(prev, last);
    }

    /** True when prior differs from session last by at least 1 bp (relative). */
    static boolean isDistinctPrior(Double prior, Double sessionClose) {
        if (prior == null || prior <= 0 || sessionClose == null || sessionClose <= 0) {
            return false;
        }
        return Math.abs(prior - sessionClose) / sessionClose >= 0.0001;
    }

    /**
     * Walk daily bars backward from the latest close to find a prior session close
     * distinct from the rolled session close (weekend / holiday collapse).
     */
    static Double resolvePriorCloseFromHistorical(MarketData historical, Double sessionClose) {
        if (historical == null) {
            return null;
        }
        List<MarketData.MarketDataPoint> points = historical.getDataPoints();
        if (points != null && points.size() >= 2) {
            for (int i = points.size() - 2; i >= 0; i--) {
                MarketData.MarketDataPoint bar = points.get(i);
                if (bar == null || bar.getOhlcData() == null || bar.getOhlcData().getClose() <= 0) {
                    continue;
                }
                double close = bar.getOhlcData().getClose();
                if (isDistinctPrior(close, sessionClose)) {
                    return close;
                }
            }
        }
        if (historical.getPreviousClose() != null
                && isDistinctPrior(historical.getPreviousClose(), sessionClose)) {
            return historical.getPreviousClose();
        }
        return null;
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
                            .minMatchScore(0.7)
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
            List<List<String>> chunks = partitionSymbols(missingSymbols);

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
                .orTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
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

                for (String cleanSymbol : fallbackSymbols) {
                    String rawSymbol = cleanToRaw.getOrDefault(cleanSymbol, cleanSymbol);
                    
                    // Directly create a flat-line chart using live price to avoid slow API timeouts
                    StockPriceDocument liveDoc = liveDocs.get(cleanSymbol);
                    if (liveDoc != null && liveDoc.getLastPrice() != null && liveDoc.getLastPrice() > 0) {
                        log.warn("[Intraday-Mongo] Symbol {} has no OHLC data in DB, skipping slow API and using flat live price: {}", 
                            cleanSymbol, liveDoc.getLastPrice());
                        com.portfolio.marketdata.model.HistoricalData flatLine = buildFlatLineChart(cleanSymbol, liveDoc.getLastPrice());
                        partial.getData().put(rawSymbol, flatLine);
                        chartCache.put(cleanSymbol + ":1D", flatLine);
                    } else {
                        log.warn("[Intraday-Mongo] Symbol {} has no data at all, omitting from chart.", cleanSymbol);
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
