package com.portfolio.redis.service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.portfolio.model.basket.cache.CachedEtfData;

import lombok.extern.slf4j.Slf4j;

/**
 * L2 Redis cache for fully enriched ETF holdings. Fail-open: never throws to callers.
 */
@Service
@Slf4j
public class BasketEtfRedisService {

    @Value("${cache.redis.enabled:true}")
    private boolean isRedisEnabled;

    @Value("${spring.data.redis.basket-etf.key-prefix:basket:etf:enriched:}")
    private String keyPrefix;

    @Value("${spring.data.redis.basket-etf.ttl:86400}")
    private Integer ttlSeconds;

    private final RedisTemplate<String, CachedEtfData> basketEtfRedisTemplate;

    public BasketEtfRedisService(@Nullable RedisTemplate<String, CachedEtfData> basketEtfRedisTemplate) {
        this.basketEtfRedisTemplate = basketEtfRedisTemplate;
    }

    public Optional<CachedEtfData> getEnrichedEtf(String symbolOrIsin) {
        if (!isUsable() || symbolOrIsin == null || symbolOrIsin.isBlank()) {
            return Optional.empty();
        }
        String key = buildKey(symbolOrIsin);
        try {
            CachedEtfData cached = basketEtfRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.info("Basket ETF L2 hit key={}", key);
                return Optional.of(cached);
            }
        } catch (Exception e) {
            log.warn("Basket ETF L2 get failed key={} — fail-open: {}", key, e.getMessage());
        }
        return Optional.empty();
    }

    @Async("taskExecutor")
    public CompletableFuture<Void> cacheEnrichedEtfAsync(String symbolOrIsin, CachedEtfData data) {
        cacheEnrichedEtf(symbolOrIsin, data);
        return CompletableFuture.completedFuture(null);
    }

    public void cacheEnrichedEtf(String symbolOrIsin, CachedEtfData data) {
        if (!isUsable() || symbolOrIsin == null || symbolOrIsin.isBlank() || data == null) {
            return;
        }
        String key = buildKey(symbolOrIsin);
        try {
            basketEtfRedisTemplate.opsForValue().set(key, data, Duration.ofSeconds(ttlSeconds));
            log.info("Basket ETF L2 put key={} ttl={}s", key, ttlSeconds);
        } catch (Exception e) {
            log.warn("Basket ETF L2 put failed key={} — fail-open: {}", key, e.getMessage());
        }
    }

    private boolean isUsable() {
        return isRedisEnabled && basketEtfRedisTemplate != null;
    }

    private String buildKey(String symbolOrIsin) {
        return keyPrefix + symbolOrIsin.trim().toUpperCase();
    }
}
