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

    @Value("${basket.cache.etf-ttl-seconds:86400}")
    private long etfL1TtlSeconds;

    private Cache<String, EtfData> l1Cache;

    public EnrichedEtfService(
            EtfApiClient etfApiClient,
            @Nullable BasketEtfRedisService basketEtfRedisService) {
        this.etfApiClient = etfApiClient;
        this.basketEtfRedisService = basketEtfRedisService;
    }

    @PostConstruct
    void initL1() {
        l1Cache = Caffeine.newBuilder()
                .expireAfterWrite(Math.max(60, etfL1TtlSeconds), TimeUnit.SECONDS)
                .maximumSize(500)
                .build();
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
        etfApiClient.enrichHoldings(live.getHoldings());
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
        if (!allHoldings.isEmpty()) {
            etfApiClient.enrichHoldings(allHoldings);
        }

        for (String q : misses) {
            EtfData data = liveBatch.get(q);
            if (data == null) {
                continue;
            }
            store(normalizeKey(q), data);
            out.put(q, copyEtf(data));
        }
        log.info("enrichment.cache=MISS batchSize={} resolved={} durationMs={}",
                misses.size(), liveBatch.size(), System.currentTimeMillis() - start);
        return out;
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
