package com.portfolio.service.intraday;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import com.am.common.amcommondata.document.price.StockPriceHistoryDocument;
import com.am.common.amcommondata.service.price.StockPriceHistoryMongoService;
import com.am.common.investment.model.historical.OHLCVTPoint;
import com.portfolio.marketdata.client.MarketDataApiClient;
import com.portfolio.marketdata.model.HistoricalChartsResponse;
import com.portfolio.marketdata.model.HistoricalData;
import com.portfolio.marketdata.service.NseIndicesService;
import com.portfolio.model.intraday.IntradayCandleWarmJobStatus;
import com.portfolio.model.intraday.IntradayCandleWarmJobStatus.Phase;

import lombok.extern.slf4j.Slf4j;

/**
 * Ops warmer: fetch session candles ({@code historical-charts?range=1D}) for a symbol
 * universe and upsert into {@code stock_price_history} so {@code /intraday} can rebuild
 * wealth without a full-book live API fan-out.
 */
@Slf4j
@Service
public class IntradayCandleWarmService {

    public static final String UNIVERSE_NIFTY_500 = "NIFTY_500";
    private static final String INDEX_NIFTY_500 = "NIFTY 500";
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int DEFAULT_CHUNK_SIZE = 10;
    private static final int MAX_PARALLEL_CHUNKS = 2;
    private static final int CHUNK_TIMEOUT_SECONDS = 45;
    private static final int MAX_FAILED_SYMBOLS_RECORDED = 50;

    private final MarketDataApiClient marketDataApiClient;
    private final NseIndicesService nseIndicesService;
    private final StockPriceHistoryMongoService stockPriceHistoryMongoService;
    private final Executor taskExecutor;

    private final ConcurrentHashMap<String, IntradayCandleWarmJobStatus> jobs = new ConcurrentHashMap<>();

    @Value("${app.intraday.candle-warm.chunk-size:" + DEFAULT_CHUNK_SIZE + "}")
    private int chunkSize = DEFAULT_CHUNK_SIZE;

    public IntradayCandleWarmService(
            MarketDataApiClient marketDataApiClient,
            NseIndicesService nseIndicesService,
            @Nullable StockPriceHistoryMongoService stockPriceHistoryMongoService,
            @Qualifier("taskExecutor") Executor taskExecutor) {
        this.marketDataApiClient = marketDataApiClient;
        this.nseIndicesService = nseIndicesService;
        this.stockPriceHistoryMongoService = stockPriceHistoryMongoService;
        this.taskExecutor = taskExecutor;
    }

    /**
     * Start an async warm job.
     *
     * @param universe {@code NIFTY_500} (default) or ignored when {@code symbolsCsv} set
     * @param symbolsCsv optional comma-separated override (dig smoke / partial warm)
     * @return job status with {@code jobId} (phase QUEUED → RUNNING)
     */
    public IntradayCandleWarmJobStatus startWarm(String universe, @Nullable String symbolsCsv) {
        if (stockPriceHistoryMongoService == null) {
            throw new IllegalStateException("StockPriceHistoryMongoService is not available");
        }

        String resolvedUniverse = (universe == null || universe.isBlank())
                ? UNIVERSE_NIFTY_500
                : universe.trim().toUpperCase();

        List<String> symbols = resolveSymbols(resolvedUniverse, symbolsCsv);
        if (symbols.isEmpty()) {
            throw new IllegalArgumentException(
                    "No symbols resolved for universe=" + resolvedUniverse
                            + (symbolsCsv != null ? " symbols=" + symbolsCsv : ""));
        }

        String jobId = UUID.randomUUID().toString();
        IntradayCandleWarmJobStatus status = IntradayCandleWarmJobStatus.builder()
                .jobId(jobId)
                .phase(Phase.QUEUED)
                .universe(symbolsCsv != null && !symbolsCsv.isBlank() ? "CUSTOM" : resolvedUniverse)
                .symbolTotal(symbols.size())
                .symbolsSucceeded(0)
                .symbolsFailed(0)
                .symbolsSkipped(0)
                .ticksWritten(0)
                .startedAt(Instant.now())
                .failedSymbols(new ArrayList<>())
                .build();
        jobs.put(jobId, status);

        List<String> snapshot = List.copyOf(symbols);
        CompletableFuture.runAsync(() -> runWarm(jobId, snapshot), taskExecutor);
        return status;
    }

    public IntradayCandleWarmJobStatus getStatus(String jobId) {
        IntradayCandleWarmJobStatus status = jobs.get(jobId);
        if (status == null) {
            return null;
        }
        return copyStatus(status);
    }

    void runWarm(String jobId, List<String> symbols) {
        IntradayCandleWarmJobStatus status = jobs.get(jobId);
        if (status == null) {
            return;
        }
        status.setPhase(Phase.RUNNING);
        log.info("[IntradayCandleWarm] jobId={} starting symbols={} chunkSize={}",
                jobId, symbols.size(), effectiveChunkSize());

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        AtomicLong ticks = new AtomicLong();
        List<String> failedSyms = Collections.synchronizedList(new ArrayList<>());

        try {
            List<List<String>> chunks = partition(symbols, effectiveChunkSize());
            for (int i = 0; i < chunks.size(); i += MAX_PARALLEL_CHUNKS) {
                List<List<String>> wave = chunks.subList(i, Math.min(i + MAX_PARALLEL_CHUNKS, chunks.size()));
                List<CompletableFuture<Void>> futures = wave.stream()
                        .map(chunk -> CompletableFuture.runAsync(
                                () -> warmChunk(chunk, succeeded, failed, skipped, ticks, failedSyms),
                                taskExecutor))
                        .collect(Collectors.toList());
                for (CompletableFuture<Void> f : futures) {
                    try {
                        f.get(CHUNK_TIMEOUT_SECONDS * 2L, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        log.warn("[IntradayCandleWarm] wave wait failed: {}", e.getMessage());
                    }
                }
                status.setSymbolsSucceeded(succeeded.get());
                status.setSymbolsFailed(failed.get());
                status.setSymbolsSkipped(skipped.get());
                status.setTicksWritten(ticks.get());
                syncFailedSymbols(status, failedSyms);
            }

            status.setPhase(Phase.DONE);
            status.setFinishedAt(Instant.now());
            log.info("[IntradayCandleWarm] jobId={} DONE ok={} fail={} skip={} ticks={}",
                    jobId, succeeded.get(), failed.get(), skipped.get(), ticks.get());
        } catch (Exception e) {
            status.setPhase(Phase.FAILED);
            status.setErrorMessage(e.getMessage());
            status.setFinishedAt(Instant.now());
            log.error("[IntradayCandleWarm] jobId={} FAILED: {}", jobId, e.getMessage(), e);
        } finally {
            status.setSymbolsSucceeded(succeeded.get());
            status.setSymbolsFailed(failed.get());
            status.setSymbolsSkipped(skipped.get());
            status.setTicksWritten(ticks.get());
            syncFailedSymbols(status, failedSyms);
        }
    }

    private void warmChunk(
            List<String> chunk,
            AtomicInteger succeeded,
            AtomicInteger failed,
            AtomicInteger skipped,
            AtomicLong ticks,
            List<String> failedSyms) {
        HistoricalChartsResponse response;
        try {
            response = marketDataApiClient.getHistoricalCharts(chunk, "1D")
                    .block(java.time.Duration.ofSeconds(CHUNK_TIMEOUT_SECONDS));
        } catch (Exception e) {
            log.warn("[IntradayCandleWarm] charts failed for chunk of {}: {}", chunk.size(), e.getMessage());
            for (String s : chunk) {
                failed.incrementAndGet();
                recordFailed(failedSyms, s);
            }
            return;
        }

        Map<String, HistoricalData> data = response != null ? response.getData() : null;
        if (data == null || data.isEmpty()) {
            for (String s : chunk) {
                failed.incrementAndGet();
                recordFailed(failedSyms, s);
            }
            return;
        }

        for (String symbol : chunk) {
            HistoricalData hd = lookupSeries(data, symbol);
            if (hd == null || hd.getDataPoints() == null || hd.getDataPoints().isEmpty()) {
                skipped.incrementAndGet();
                continue;
            }
            List<StockPriceHistoryDocument> docs = toHistoryDocs(symbol, hd.getDataPoints());
            if (docs.isEmpty()) {
                skipped.incrementAndGet();
                continue;
            }
            try {
                int written = stockPriceHistoryMongoService.saveSessionTicks(docs);
                ticks.addAndGet(written);
                succeeded.incrementAndGet();
            } catch (Exception e) {
                log.warn("[IntradayCandleWarm] save failed for {}: {}", symbol, e.getMessage());
                failed.incrementAndGet();
                recordFailed(failedSyms, symbol);
            }
        }
    }

    /**
     * Map candle closes to history ticks (package-visible for unit tests).
     */
    static List<StockPriceHistoryDocument> toHistoryDocs(String symbol, List<OHLCVTPoint> points) {
        if (symbol == null || symbol.isBlank() || points == null || points.isEmpty()) {
            return List.of();
        }
        String clean = symbol.trim();
        Date now = new Date();
        List<StockPriceHistoryDocument> out = new ArrayList<>();
        for (OHLCVTPoint p : points) {
            if (p == null || p.getClose() == null || p.getClose() <= 0 || p.getTime() == null) {
                continue;
            }
            long minute = toMinuteEpoch(p.getTime());
            out.add(StockPriceHistoryDocument.builder()
                    .id(clean + "_" + minute)
                    .symbol(clean)
                    .price(p.getClose())
                    .timestampMinute(minute)
                    .createdAt(now)
                    .build());
        }
        return out;
    }

    static long toMinuteEpoch(LocalDateTime time) {
        return time.atZone(IST).truncatedTo(ChronoUnit.MINUTES).toInstant().toEpochMilli();
    }

    private List<String> resolveSymbols(String universe, @Nullable String symbolsCsv) {
        if (symbolsCsv != null && !symbolsCsv.isBlank()) {
            return Arrays.stream(symbolsCsv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
        }
        if (!UNIVERSE_NIFTY_500.equalsIgnoreCase(universe) && !"NIFTY 500".equalsIgnoreCase(universe)) {
            throw new IllegalArgumentException("Unsupported universe=" + universe + "; use NIFTY_500 or symbols=");
        }
        List<String> fromIndex = nseIndicesService.getConstituentSymbols(INDEX_NIFTY_500);
        if (fromIndex == null) {
            return List.of();
        }
        return fromIndex.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
    }

    private static HistoricalData lookupSeries(Map<String, HistoricalData> data, String symbol) {
        HistoricalData hd = data.get(symbol);
        if (hd != null) {
            return hd;
        }
        for (Map.Entry<String, HistoricalData> e : data.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(symbol)) {
                return e.getValue();
            }
            // market may return NSE_EQ:SYMBOL keys
            if (e.getKey() != null && e.getKey().regionMatches(true, 0, "NSE_EQ:", 0, 7)
                    && e.getKey().substring(7).equalsIgnoreCase(symbol)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static List<List<String>> partition(List<String> symbols, int size) {
        if (symbols.isEmpty()) {
            return List.of();
        }
        int n = Math.max(1, size);
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < symbols.size(); i += n) {
            chunks.add(symbols.subList(i, Math.min(i + n, symbols.size())));
        }
        return chunks;
    }

    private int effectiveChunkSize() {
        return chunkSize > 0 ? chunkSize : DEFAULT_CHUNK_SIZE;
    }

    private static void recordFailed(List<String> failedSyms, String symbol) {
        if (failedSyms.size() < MAX_FAILED_SYMBOLS_RECORDED) {
            failedSyms.add(symbol);
        }
    }

    private static void syncFailedSymbols(IntradayCandleWarmJobStatus status, List<String> failedSyms) {
        status.setFailedSymbols(new ArrayList<>(failedSyms));
    }

    private static IntradayCandleWarmJobStatus copyStatus(IntradayCandleWarmJobStatus s) {
        return IntradayCandleWarmJobStatus.builder()
                .jobId(s.getJobId())
                .phase(s.getPhase())
                .universe(s.getUniverse())
                .symbolTotal(s.getSymbolTotal())
                .symbolsSucceeded(s.getSymbolsSucceeded())
                .symbolsFailed(s.getSymbolsFailed())
                .symbolsSkipped(s.getSymbolsSkipped())
                .ticksWritten(s.getTicksWritten())
                .startedAt(s.getStartedAt())
                .finishedAt(s.getFinishedAt())
                .errorMessage(s.getErrorMessage())
                .failedSymbols(s.getFailedSymbols() != null
                        ? new ArrayList<>(s.getFailedSymbols())
                        : new ArrayList<>())
                .build();
    }
}
