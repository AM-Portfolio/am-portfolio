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
import java.util.concurrent.TimeUnit;

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

    @Value("${basket.cache.etf-ttl-seconds:86400}")
    private long etfL1TtlSeconds;

    private Cache<String, EtfData> l1Cache;

    public EnrichedEtfService(
            EtfApiClient etfApiClient,
            @Nullable BasketEtfRedisService basketEtfRedisService,
            BasketCatalogService basketCatalogService) {
        this.etfApiClient = etfApiClient;
        this.basketEtfRedisService = basketEtfRedisService;
        this.basketCatalogService = basketCatalogService;
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
                log.info("Pre-warming ETF cache for {} ETFs", topEtfs.size());
                getEnrichedEtfsBatch(topEtfs);
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
        if (l1 != null) {
            log.info("enrichment.cache=L1 key={}", key);
            return copyEtf(l1);
        }

        if (basketEtfRedisService != null) {
            try {
                var cached = basketEtfRedisService.getEnrichedEtf(key);
                if (cached.isPresent()) {
                    EtfData fromL2 = fromCached(cached.get());
                    l1Cache.put(key, fromL2);
                    if (fromL2.getSymbol() != null) {
                        l1Cache.put(normalizeKey(fromL2.getSymbol()), fromL2);
                    }
                    log.info("enrichment.cache=L2 key={}", key);
                    return copyEtf(fromL2);
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
    public Map<String, EtfData> getEnrichedEtfsBatch(List<String> queries) {
        return getEnrichedEtfsBatch(queries, false);
    }

    /**
     * @param discoverFastPath when true, skip market enrichHoldings if ≥95% holdings have valid ISINs.
     */
    public Map<String, EtfData> getEnrichedEtfsBatch(List<String> queries, boolean discoverFastPath) {
        Map<String, EtfData> out = new LinkedHashMap<>();
        if (queries == null || queries.isEmpty()) {
            return out;
        }

        List<String> misses = new ArrayList<>();
        for (String q : queries) {
            if (q == null || q.isBlank()) {
                continue;
            }
            String key = normalizeKey(q);
            EtfData l1 = l1Cache.getIfPresent(key);
            if (l1 != null) {
                out.put(q, copyEtf(l1));
                log.debug("enrichment.cache=L1 key={}", key);
                continue;
            }
            if (basketEtfRedisService != null) {
                try {
                    var cached = basketEtfRedisService.getEnrichedEtf(key);
                    if (cached.isPresent()) {
                        EtfData fromL2 = fromCached(cached.get());
                        l1Cache.put(key, fromL2);
                        out.put(q, copyEtf(fromL2));
                        log.info("enrichment.cache=L2 key={}", key);
                        continue;
                    }
                } catch (Exception e) {
                    log.warn("enrichment.cache=L2_FAIL key={} — fail-open: {}", key, e.getMessage());
                }
            }
            misses.add(q);
        }

        if (misses.isEmpty()) {
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
            if (discoverFastPath && isinCoverage(allHoldings) >= 0.95) {
                log.info("basket.opp.stage=enrich skipped=true reason=isinCoverage discoverFastPath=true holdings={}",
                        allHoldings.size());
            } else {
                etfApiClient.enrichHoldings(allHoldings);
                ranEnrich = true;
            }
        }
        log.info("basket.opp.stage=enrich ran={} durationMs={} discoverFastPath={}",
                ranEnrich, System.currentTimeMillis() - enrichStart, discoverFastPath);

        for (String q : misses) {
            EtfData data = liveBatch.get(q);
            if (data == null) {
                continue;
            }
            store(normalizeKey(q), data);
            out.put(q, copyEtf(data));
        }
        log.info("enrichment.cache=MISS batchSize={} resolved={} durationMs={} discoverFastPath={}",
                misses.size(), liveBatch.size(), System.currentTimeMillis() - start, discoverFastPath);
        return out;
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

    private void store(String key, EtfData data) {
        if (data == null) {
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
