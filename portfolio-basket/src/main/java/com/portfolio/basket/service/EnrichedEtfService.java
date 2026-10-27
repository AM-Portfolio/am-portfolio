package com.portfolio.basket.service;

import com.portfolio.basket.client.EtfApiClient;
import com.portfolio.basket.model.EtfData;
import com.portfolio.basket.model.EtfHolding;
import com.portfolio.model.basket.cache.CachedEtfData;
import com.portfolio.model.basket.cache.CachedEtfHolding;
import com.portfolio.redis.service.BasketEtfRedisService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Shared ETF load + enrichment with L1 (always-on in-pod) and optional L2 Redis.
 * Redis is fail-open: misses or failures fall through to live path.
 */
@Service
@Slf4j
public class EnrichedEtfService {

    private final EtfApiClient etfApiClient;
    private final BasketEtfRedisService basketEtfRedisService;
    private final BasketCatalogService basketCatalogService;
    private final EtfPerformanceEnricher performanceEnricher;

    @Value("${basket.cache.etf-ttl-seconds:86400}")
    private long etfL1TtlSeconds;

    /** When true with discoverFastPath, skip 5Y weekly hist; miss path uses one monthly 5Y ALL. */
    @Value("${basket.opportunities.discover-skip-perf-hist:true}")
    private boolean discoverSkipPerfHist;

    private Cache<String, EtfData> l1Cache;
    private final ConcurrentHashMap<String, CompletableFuture<Void>> discoverWaves = new ConcurrentHashMap<>();

    public EnrichedEtfService(
            EtfApiClient etfApiClient,
            @Nullable BasketEtfRedisService basketEtfRedisService,
            BasketCatalogService basketCatalogService,
            EtfPerformanceEnricher performanceEnricher) {
        this.etfApiClient = etfApiClient;
        this.basketEtfRedisService = basketEtfRedisService;
        this.basketCatalogService = basketCatalogService;
        this.performanceEnricher = performanceEnricher;
    }

    @PostConstruct
    void initL1() {
        l1Cache = Caffeine.newBuilder()
                .expireAfterWrite(Math.max(60, etfL1TtlSeconds), TimeUnit.SECONDS)
                .maximumSize(500)
                .build();
    }

    @Async
    @PostConstruct
    public void warmCache() {
        try {
            List<String> topEtfs = basketCatalogService.getTopEtfSymbols();
            if (topEtfs != null && !topEtfs.isEmpty()) {
                log.info("Pre-warming ETF cache for {} ETFs (discover monthly wave)", topEtfs.size());
                getEnrichedEtfsBatch(topEtfs, true, DISCOVER_WARM_WAIT_MS);
            }
        } catch (Exception e) {
            log.warn("ETF cache warm-up failed (non-fatal): {}", e.getMessage());
        }
    }

    public EtfData getEnrichedEtf(String symbolOrIsin) {
        if (symbolOrIsin == null || symbolOrIsin.isBlank()) {
            return null;
        }
        String key = normalizeKey(symbolOrIsin);

        EtfData l1 = l1Cache.getIfPresent(key);
        if (l1 != null && hasUsableHoldings(l1)) {
            EtfData copy = copyEtf(l1);
            if (completePerformanceIfNeeded(copy, key)) {
                log.info("enrichment.cache=L1_PERF_REPAIR key={}", key);
            } else {
                log.info("enrichment.cache=L1 key={}", key);
            }
            return copy;
        }
        if (l1 != null) {
            l1Cache.invalidate(key);
            log.info("enrichment.cache=L1_STALE_EMPTY key={} — refetch", key);
        }

        if (basketEtfRedisService != null) {
            try {
                var cached = basketEtfRedisService.getEnrichedEtf(key);
                if (cached.isPresent()) {
                    EtfData fromL2 = fromCached(cached.get());
                    if (!hasUsableHoldings(fromL2)) {
                        log.info("enrichment.cache=L2_STALE_EMPTY key={} — refetch", key);
                    } else {
                        completePerformanceIfNeeded(fromL2, key);
                        store(key, fromL2);
                        log.info("enrichment.cache=L2 key={}", key);
                        return copyEtf(fromL2);
                    }
                }
            } catch (Exception e) {
                log.warn("enrichment.cache=L2_FAIL key={} — fail-open: {}", key, e.getMessage());
            }
        }

        long start = System.currentTimeMillis();
        EtfData live = etfApiClient.fetchEtfHoldings(symbolOrIsin);
        if (live == null) {
            log.info("enrichment.cache=MISS key={} live=null durationMs={}", key, System.currentTimeMillis() - start);
            return null;
        }
        if (live.getHoldings() != null && !live.getHoldings().isEmpty()
                && isinCoverage(live.getHoldings()) >= 0.95) {
            log.info("basket.preview.stage=enrich skipped=true reason=isinCoverage key={} holdings={}",
                    key, live.getHoldings().size());
        } else if (live.getHoldings() != null && !live.getHoldings().isEmpty()) {
            etfApiClient.enrichHoldings(live.getHoldings());
        }
        performanceEnricher.fillMissing(List.of(live));
        store(key, live);
        log.info("enrichment.cache=MISS key={} holdings={} durationMs={}",
                key,
                live.getHoldings() != null ? live.getHoldings().size() : 0,
                System.currentTimeMillis() - start);
        return copyEtf(live);
    }

    /**
     * Batch resolve + enrich with global ISIN enrichment dedup and L1/L2 reuse.
     */
    public static final long DISCOVER_REQUEST_WAIT_MS = 2_500L;
    public static final long DISCOVER_WARM_WAIT_MS = 25_000L;

    public Map<String, EtfData> getEnrichedEtfsBatch(List<String> queries) {
        return getEnrichedEtfsBatch(queries, false);
    }

    /**
     * @param discoverFastPath when true, skip holdings enrich + weekly hist; miss path fills
     *                         1Y/3Y/5Y + spark from one 5Y monthly ALL wave.
     */
    public Map<String, EtfData> getEnrichedEtfsBatch(List<String> queries, boolean discoverFastPath) {
        return getEnrichedEtfsBatch(queries, discoverFastPath, DISCOVER_REQUEST_WAIT_MS);
    }

    public Map<String, EtfData> getEnrichedEtfsBatch(
            List<String> queries, boolean discoverFastPath, long discoverWaitMs) {
        Map<String, EtfData> out = new LinkedHashMap<>();
        if (queries == null || queries.isEmpty()) {
            return out;
        }

        boolean skipWeeklyHist = discoverFastPath && discoverSkipPerfHist;

        List<String> misses = new ArrayList<>();
        List<EtfData> cachedNeedingPerf = new ArrayList<>();
        List<EtfData> cachedNeedingDiscover = new ArrayList<>();
        for (String q : queries) {
            if (q == null || q.isBlank()) {
                continue;
            }
            String key = normalizeKey(q);
            EtfData l1 = l1Cache.getIfPresent(key);
            boolean l1Ok = discoverFastPath
                    ? (hasMatchReadyHoldings(l1) || hasCompleteDiscoverPerformance(l1))
                    : hasUsableHoldings(l1);
            if (l1 != null && l1Ok) {
                EtfData copy = copyEtf(l1);
                out.put(q, copy);
                if (skipWeeklyHist && EtfPerformanceEnricher.needsPerformanceFill(copy)) {
                    cachedNeedingDiscover.add(copy);
                } else if (!skipWeeklyHist && EtfPerformanceEnricher.needsPerformanceFill(copy)) {
                    cachedNeedingPerf.add(copy);
                } else {
                    log.debug("enrichment.cache=L1 key={}", key);
                }
                continue;
            }
            if (l1 != null) {
                l1Cache.invalidate(key);
                log.info("enrichment.cache=L1_STALE key={} discoverFastPath={} — refetch", key, discoverFastPath);
            }
            if (basketEtfRedisService != null) {
                try {
                    var cached = basketEtfRedisService.getEnrichedEtf(key);
                    if (cached.isPresent()) {
                        EtfData fromL2 = fromCached(cached.get());
                        boolean l2Ok = discoverFastPath
                                ? (hasMatchReadyHoldings(fromL2) || hasCompleteDiscoverPerformance(fromL2))
                                : hasUsableHoldings(fromL2);
                        if (!l2Ok) {
                            log.info("enrichment.cache=L2_STALE key={} discoverFastPath={} — refetch",
                                    key, discoverFastPath);
                        } else if (skipWeeklyHist && EtfPerformanceEnricher.needsPerformanceFill(fromL2)) {
                            cachedNeedingDiscover.add(fromL2);
                            out.put(q, fromL2);
                            continue;
                        } else if (!skipWeeklyHist && EtfPerformanceEnricher.needsPerformanceFill(fromL2)) {
                            cachedNeedingPerf.add(fromL2);
                            out.put(q, fromL2);
                            continue;
                        } else {
                            l1Cache.put(key, fromL2);
                            out.put(q, copyEtf(fromL2));
                            log.info("enrichment.cache=L2 key={}", key);
                            continue;
                        }
                    }
                } catch (Exception e) {
                    log.warn("enrichment.cache=L2_FAIL key={} — fail-open: {}", key, e.getMessage());
                }
            }
            misses.add(q);
        }

        if (!cachedNeedingPerf.isEmpty()) {
            performanceEnricher.fillMissing(cachedNeedingPerf);
            for (EtfData repaired : cachedNeedingPerf) {
                if (repaired.getSymbol() != null) {
                    store(normalizeKey(repaired.getSymbol()), repaired);
                }
            }
        }

        if (misses.isEmpty()) {
            if (skipWeeklyHist && !cachedNeedingDiscover.isEmpty()) {
                fillDiscoverWaveSingleflight(cachedNeedingDiscover, discoverWaitMs);
            } else if (skipWeeklyHist) {
                log.info("basket.opp.stage=perf skipped=true reason=completeCacheHit");
            }
            return out;
        }

        long start = System.currentTimeMillis();
        Map<String, EtfData> liveBatch = etfApiClient.fetchEtfHoldingsBatch(misses);

        List<EtfHolding> allHoldings = new ArrayList<>();
        for (EtfData data : liveBatch.values()) {
            if (data != null && data.getHoldings() != null) {
                allHoldings.addAll(data.getHoldings());
            }
        }
        long enrichStart = System.currentTimeMillis();
        boolean ranEnrich = false;
        if (!allHoldings.isEmpty()) {
            double cov = isinCoverage(allHoldings);
            // Discover skips batch-search only when parser already supplied ISINs for match.
            if (discoverFastPath && cov >= 0.5) {
                log.info("basket.opp.stage=enrich skipped=true reason=discoverFastPath isinCoverage={} holdings={}",
                        String.format(Locale.ROOT, "%.2f", cov), allHoldings.size());
            } else {
                etfApiClient.enrichHoldings(allHoldings);
                ranEnrich = true;
            }
        }
        log.info("basket.opp.stage=enrich ran={} durationMs={} discoverFastPath={}",
                ranEnrich, System.currentTimeMillis() - enrichStart, discoverFastPath);

        List<EtfData> liveList = new ArrayList<>();
        for (String q : misses) {
            EtfData data = liveBatch.get(q);
            if (data == null) {
                // Parser miss (e.g. GOLDBEES holdings gap): still expose Discover card.
                data = stubEtf(q);
                liveBatch.put(q, data);
                log.warn("enrichment.stub symbol={} reason=parser_miss", normalizeKey(q));
            }
            if (data.getSymbol() == null || data.getSymbol().isBlank()) {
                data.setSymbol(normalizeKey(q));
            }
            liveList.add(data);
        }
        if (!skipWeeklyHist) {
            performanceEnricher.fillMissing(liveList);
        } else {
            List<EtfData> discoverFill = new ArrayList<>(cachedNeedingDiscover);
            discoverFill.addAll(liveList);
            fillDiscoverWaveSingleflight(discoverFill, discoverWaitMs);
        }

        for (String q : misses) {
            EtfData data = liveBatch.get(q);
            if (data == null) {
                continue;
            }
            store(normalizeKey(q), data);
            out.put(q, copyEtf(data));
        }
        log.info("enrichment.cache=MISS batchSize={} resolved={} durationMs={} discoverFastPath={} skipWeeklyHist={}",
                misses.size(), liveBatch.size(), System.currentTimeMillis() - start, discoverFastPath, skipWeeklyHist);
        return out;
    }

    private void fillDiscoverWaveSingleflight(List<EtfData> needing, long waitMs) {
        if (needing == null || needing.isEmpty()) {
            return;
        }
        List<EtfData> toFill = needing.stream()
                .filter(EtfPerformanceEnricher::needsPerformanceFill)
                .filter(e -> e.getSymbol() != null && !e.getSymbol().isBlank())
                .collect(Collectors.toList());
        if (toFill.isEmpty()) {
            return;
        }
        String waveKey = toFill.stream()
                .map(e -> normalizeKey(e.getSymbol()))
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));
        long wait = Math.max(50L, waitMs);
        CompletableFuture<Void> created = new CompletableFuture<>();
        CompletableFuture<Void> existing = discoverWaves.putIfAbsent(waveKey, created);
        if (existing != null) {
            try {
                existing.get(wait, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.warn("basket.perf.discover.wave wait failed: {}", e.getMessage());
            }
            overlayPerformanceFromL1(toFill);
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                performanceEnricher.fillDiscoverWave(toFill, 60_000L);
                for (EtfData repaired : toFill) {
                    if (repaired.getSymbol() != null) {
                        store(normalizeKey(repaired.getSymbol()), repaired);
                    }
                }
                created.complete(null);
            } catch (Exception e) {
                created.completeExceptionally(e);
                log.warn("basket.perf.discover.wave leader failed: {}", e.getMessage());
            } finally {
                discoverWaves.remove(waveKey, created);
            }
        });
        try {
            created.get(wait, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("basket.perf.discover.wave request-wait timeoutMs={} symbols={}", wait, toFill.size());
        }
        overlayPerformanceFromL1(toFill);
    }

    private void overlayPerformanceFromL1(List<EtfData> targets) {
        if (targets == null) {
            return;
        }
        for (EtfData target : targets) {
            if (target == null || target.getSymbol() == null) {
                continue;
            }
            EtfData cached = l1Cache.getIfPresent(normalizeKey(target.getSymbol()));
            if (cached == null) {
                continue;
            }
            target.setReturn1Y(cached.getReturn1Y());
            target.setReturn3Y(cached.getReturn3Y());
            target.setReturn5Y(cached.getReturn5Y());
            target.setReturnsAsOf(cached.getReturnsAsOf());
            if (cached.getSparklineCloses() != null) {
                target.setSparklineCloses(new ArrayList<>(cached.getSparklineCloses()));
            }
        }
    }

    static EtfData stubEtf(String query) {
        EtfData data = new EtfData();
        String symbol = query == null ? null : query.trim().toUpperCase(Locale.ROOT);
        data.setSymbol(symbol);
        data.setName(symbol);
        data.setHoldings(List.of());
        return data;
    }

    /**
     * @return true when performance fields were repaired and re-cached
     */
    private boolean completePerformanceIfNeeded(EtfData data, String key) {
        if (!EtfPerformanceEnricher.needsPerformanceFill(data)) {
            return false;
        }
        int filled = performanceEnricher.fillMissing(List.of(data));
        if (filled > 0 || !EtfPerformanceEnricher.needsPerformanceFill(data)) {
            store(key, data);
            return filled > 0;
        }
        return false;
    }

    static double isinCoverage(List<EtfHolding> holdings) {
        if (holdings == null || holdings.isEmpty()) {
            return 0.0;
        }
        int ok = 0;
        for (EtfHolding h : holdings) {
            if (h.getIsin() != null && h.getIsin().length() >= 10 && !"-".equals(h.getIsin())) {
                ok++;
            }
        }
        return (double) ok / holdings.size();
    }

    /** Empty parser stubs must not poison L1/L2 or Discover stays at 0 held forever. */
    static boolean hasUsableHoldings(EtfData data) {
        return data != null && data.getHoldings() != null && !data.getHoldings().isEmpty();
    }

    /** Discover match is ISIN-based — reject cache entries that lack constituent ISINs. */
    static boolean hasMatchReadyHoldings(EtfData data) {
        return hasUsableHoldings(data) && isinCoverage(data.getHoldings()) >= 0.5;
    }

    static boolean hasCompleteDiscoverPerformance(EtfData data) {
        return data != null && !EtfPerformanceEnricher.needsPerformanceFill(data);
    }

    static boolean hasAnyDiscoverPerformance(EtfData data) {
        if (data == null) {
            return false;
        }
        if (data.getReturn1Y() != null || data.getReturn3Y() != null || data.getReturn5Y() != null) {
            return true;
        }
        return data.getSparklineCloses() != null && data.getSparklineCloses().size() >= 2;
    }

    private void store(String key, EtfData data) {
        if (data == null) {
            return;
        }
        if (!hasUsableHoldings(data) && !hasAnyDiscoverPerformance(data)) {
            log.info("enrichment.cache=SKIP_EMPTY key={}", key);
            return;
        }
        EtfData snapshot = copyEtf(data);
        l1Cache.put(key, snapshot);
        if (snapshot.getSymbol() != null && !snapshot.getSymbol().isBlank()) {
            l1Cache.put(normalizeKey(snapshot.getSymbol()), snapshot);
        }
        if (basketEtfRedisService != null) {
            try {
                CachedEtfData cached = toCached(snapshot);
                basketEtfRedisService.cacheEnrichedEtfAsync(key, cached);
                if (snapshot.getSymbol() != null) {
                    basketEtfRedisService.cacheEnrichedEtfAsync(snapshot.getSymbol(), cached);
                }
            } catch (Exception e) {
                log.warn("enrichment.cache=L2_WRITE_FAIL key={} — fail-open: {}", key, e.getMessage());
            }
        }
    }

    private static String normalizeKey(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    static CachedEtfData toCached(EtfData data) {
        CachedEtfData cached = new CachedEtfData();
        cached.setSymbol(data.getSymbol());
        cached.setName(data.getName());
        cached.setCategoryLabel(data.getCategoryLabel());
        cached.setReturn1Y(data.getReturn1Y());
        cached.setReturn3Y(data.getReturn3Y());
        cached.setReturn5Y(data.getReturn5Y());
        cached.setReturnsAsOf(data.getReturnsAsOf());
        cached.setSparklineCloses(
                data.getSparklineCloses() != null ? new ArrayList<>(data.getSparklineCloses()) : null);
        List<CachedEtfHolding> holdings = new ArrayList<>();
        if (data.getHoldings() != null) {
            for (EtfHolding h : data.getHoldings()) {
                CachedEtfHolding ch = new CachedEtfHolding();
                ch.setIsin(h.getIsin());
                ch.setSymbol(h.getSymbol());
                ch.setSector(h.getSector());
                ch.setWeight(h.getWeight());
                ch.setMarketCapCategory(h.getMarketCapCategory());
                ch.setMarketCapValue(h.getMarketCapValue());
                holdings.add(ch);
            }
        }
        cached.setHoldings(holdings);
        return cached;
    }

    static EtfData fromCached(CachedEtfData cached) {
        EtfData data = new EtfData();
        data.setSymbol(cached.getSymbol());
        data.setName(cached.getName());
        data.setCategoryLabel(cached.getCategoryLabel());
        data.setReturn1Y(cached.getReturn1Y());
        data.setReturn3Y(cached.getReturn3Y());
        data.setReturn5Y(cached.getReturn5Y());
        data.setReturnsAsOf(cached.getReturnsAsOf());
        data.setSparklineCloses(
                cached.getSparklineCloses() != null ? new ArrayList<>(cached.getSparklineCloses()) : null);
        List<EtfHolding> holdings = new ArrayList<>();
        if (cached.getHoldings() != null) {
            for (CachedEtfHolding ch : cached.getHoldings()) {
                EtfHolding h = new EtfHolding();
                h.setIsin(ch.getIsin());
                h.setSymbol(ch.getSymbol());
                h.setSector(ch.getSector());
                h.setWeight(ch.getWeight());
                h.setMarketCapCategory(ch.getMarketCapCategory());
                h.setMarketCapValue(ch.getMarketCapValue());
                holdings.add(h);
            }
        }
        data.setHoldings(holdings);
        return data;
    }

    static EtfData copyEtf(EtfData src) {
        if (src == null) {
            return null;
        }
        return fromCached(toCached(src));
    }
}
