package com.portfolio.redis.service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import com.portfolio.model.analytics.intelligence.PortfolioIntelligenceResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Fail-open Redis cache for portfolio intelligence Overview responses (WS7).
 * In-process L1 covers cases where Redis put/get silently no-ops (e.g. GrowthBook flag).
 */
@Service
@Slf4j
public class PortfolioIntelligenceRedisService {

    @Value("${cache.redis.enabled:true}")
    private boolean isRedisEnabled;

    @Value("${spring.data.redis.portfolio-intel.key-prefix:portfolio:intel:v1:}")
    private String keyPrefix;

    @Value("${spring.data.redis.portfolio-intel.ttl:90}")
    private int ttlSeconds;

    private static final long L1_TTL_MS = 45_000L;

    private final RedisTemplate<String, PortfolioIntelligenceResponse> portfolioIntelligenceRedisTemplate;
    private final ConcurrentHashMap<String, L1Entry> l1 = new ConcurrentHashMap<>();

    private record L1Entry(PortfolioIntelligenceResponse response, long expiresAtMs) {}

    public PortfolioIntelligenceRedisService(
            @Nullable RedisTemplate<String, PortfolioIntelligenceResponse> portfolioIntelligenceRedisTemplate) {
        this.portfolioIntelligenceRedisTemplate = portfolioIntelligenceRedisTemplate;
    }

    public Optional<PortfolioIntelligenceResponse> get(String portfolioId) {
        if (portfolioId == null || portfolioId.isBlank()) {
            return Optional.empty();
        }
        String key = buildKey(portfolioId);
        long now = System.currentTimeMillis();
        L1Entry local = l1.get(key);
        if (local != null && local.expiresAtMs() > now) {
            log.debug("Intel L1 hit key={}", key);
            return Optional.of(local.response());
        }

        if (!isUsable()) {
            return Optional.empty();
        }
        try {
            PortfolioIntelligenceResponse cached = portfolioIntelligenceRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.debug("Intel L2 hit key={}", key);
                l1.put(key, new L1Entry(cached, now + L1_TTL_MS));
                return Optional.of(cached);
            }
        } catch (Exception e) {
            log.warn("Intel L2 get failed key={} — fail-open: {}", key, e.getMessage());
        }
        return Optional.empty();
    }

    public void put(String portfolioId, PortfolioIntelligenceResponse response) {
        if (portfolioId == null || portfolioId.isBlank() || response == null) {
            return;
        }
        String key = buildKey(portfolioId);
        long now = System.currentTimeMillis();
        l1.put(key, new L1Entry(response, now + L1_TTL_MS));

        if (!isUsable()) {
            return;
        }
        try {
            portfolioIntelligenceRedisTemplate.opsForValue().set(
                    key, response, Duration.ofSeconds(Math.max(1, ttlSeconds)));
            log.debug("Intel L2 put key={} ttl={}s", key, ttlSeconds);
        } catch (Exception e) {
            log.warn("Intel L2 put failed key={} — fail-open: {}", key, e.getMessage());
        }
    }

    /** Drop cached intelligence so Class weights refresh after holdings writes. */
    public void evict(String portfolioId) {
        if (portfolioId == null || portfolioId.isBlank()) {
            return;
        }
        String key = buildKey(portfolioId);
        l1.remove(key);
        if (!isUsable()) {
            return;
        }
        try {
            portfolioIntelligenceRedisTemplate.delete(key);
            log.debug("Intel L2 evict key={}", key);
        } catch (Exception e) {
            log.warn("Intel L2 evict failed key={} — fail-open: {}", key, e.getMessage());
        }
    }

    /** Evict All-Portfolios aggregate intel for the owner (key {@code user:{userId}:all}). */
    public void evictAggregateForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        evict("user:" + userId.trim() + ":all");
    }

    private boolean isUsable() {
        return isRedisEnabled && portfolioIntelligenceRedisTemplate != null;
    }

    /** Stable key; Redis TTL alone invalidates stale entries. */
    private String buildKey(String portfolioId) {
        return keyPrefix + portfolioId;
    }
}
