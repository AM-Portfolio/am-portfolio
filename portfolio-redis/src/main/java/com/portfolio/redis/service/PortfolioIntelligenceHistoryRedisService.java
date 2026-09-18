package com.portfolio.redis.service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import com.portfolio.model.analytics.intelligence.CachedIntelligenceHistory;

import lombok.extern.slf4j.Slf4j;

/**
 * Fail-open L1 (in-process) + L2 (Redis) cache for intelligence history / β.
 */
@Service
@Slf4j
public class PortfolioIntelligenceHistoryRedisService {

    private static final int MIN_POINTS = 20;

    @Value("${cache.redis.enabled:true}")
    private boolean isRedisEnabled;

    @Value("${spring.data.redis.portfolio-intel-hist.key-prefix:portfolio:intel-hist:v1:}")
    private String keyPrefix;

    @Value("${spring.data.redis.portfolio-intel-hist.ttl:300}")
    private int ttlSeconds;

    private final RedisTemplate<String, CachedIntelligenceHistory> template;
    private final ConcurrentHashMap<String, L1Entry> l1 = new ConcurrentHashMap<>();

    public PortfolioIntelligenceHistoryRedisService(
            @Nullable RedisTemplate<String, CachedIntelligenceHistory> portfolioIntelligenceHistoryRedisTemplate) {
        this.template = portfolioIntelligenceHistoryRedisTemplate;
    }

    public Optional<CachedIntelligenceHistory> get(String portfolioId) {
        if (portfolioId == null || portfolioId.isBlank()) {
            return Optional.empty();
        }
        L1Entry local = l1.get(portfolioId);
        if (local != null && !local.expired(ttlSeconds)) {
            return Optional.of(local.value);
        }
        if (local != null) {
            l1.remove(portfolioId, local);
        }
        if (!isRedisUsable()) {
            return Optional.empty();
        }
        String key = buildKey(portfolioId);
        try {
            CachedIntelligenceHistory cached = template.opsForValue().get(key);
            if (cached != null && isUsable(cached)) {
                l1.put(portfolioId, new L1Entry(cached, System.currentTimeMillis()));
                log.debug("Intel hist L2 hit key={}", key);
                return Optional.of(cached);
            }
        } catch (Exception e) {
            log.warn("Intel hist L2 get failed key={} — fail-open: {}", key, e.getMessage());
        }
        return Optional.empty();
    }

    public void put(String portfolioId, CachedIntelligenceHistory value) {
        if (portfolioId == null || portfolioId.isBlank() || !isUsable(value)) {
            return;
        }
        CachedIntelligenceHistory toStore = CachedIntelligenceHistory.builder()
                .historyPoints(value.getHistoryPoints())
                .portRetPct(value.getPortRetPct())
                .niftyRetPct(value.getNiftyRetPct())
                .dailyVolPct(value.getDailyVolPct())
                .beta(value.getBeta())
                .computedAtEpochMs(System.currentTimeMillis())
                .build();
        l1.put(portfolioId, new L1Entry(toStore, System.currentTimeMillis()));
        if (!isRedisUsable()) {
            return;
        }
        String key = buildKey(portfolioId);
        try {
            template.opsForValue().set(key, toStore, Duration.ofSeconds(Math.max(1, ttlSeconds)));
            log.debug("Intel hist L2 put key={} ttl={}s", key, ttlSeconds);
        } catch (Exception e) {
            log.warn("Intel hist L2 put failed key={} — fail-open: {}", key, e.getMessage());
        }
    }

    private static boolean isUsable(CachedIntelligenceHistory c) {
        return c != null
                && c.getHistoryPoints() >= MIN_POINTS
                && c.getBeta() != null
                && c.getBeta() > 0
                && Double.isFinite(c.getBeta());
    }

    private boolean isRedisUsable() {
        return isRedisEnabled && template != null;
    }

    private String buildKey(String portfolioId) {
        return keyPrefix + portfolioId;
    }

    private record L1Entry(CachedIntelligenceHistory value, long storedAtMs) {
        boolean expired(int ttlSec) {
            return System.currentTimeMillis() - storedAtMs > Math.max(1, ttlSec) * 1000L;
        }
    }
}
