package com.portfolio.redis.service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.portfolio.model.basket.cache.CachedBasketCatalog;

import lombok.extern.slf4j.Slf4j;

/**
 * L2 Redis cache for basket catalog. Fail-open: never throws to callers.
 */
@Service
@Slf4j
public class BasketCatalogRedisService {

    @Value("${cache.redis.enabled:true}")
    private boolean isRedisEnabled;

    @Value("${spring.data.redis.basket-catalog.key-prefix:basket:catalog:}")
    private String keyPrefix;

    @Value("${spring.data.redis.basket-catalog.ttl:3600}")
    private Integer ttlSeconds;

    private final RedisTemplate<String, CachedBasketCatalog> basketCatalogRedisTemplate;

    public BasketCatalogRedisService(
            @Nullable RedisTemplate<String, CachedBasketCatalog> basketCatalogRedisTemplate) {
        this.basketCatalogRedisTemplate = basketCatalogRedisTemplate;
    }

    public Optional<CachedBasketCatalog> getCatalog() {
        if (!isUsable()) {
            return Optional.empty();
        }
        String key = catalogKey();
        try {
            CachedBasketCatalog cached = basketCatalogRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.info("Basket catalog L2 hit key={}", key);
                return Optional.of(cached);
            }
        } catch (Exception e) {
            log.warn("Basket catalog L2 get failed key={} — fail-open: {}", key, e.getMessage());
        }
        return Optional.empty();
    }

    @Async("taskExecutor")
    public CompletableFuture<Void> cacheCatalogAsync(CachedBasketCatalog catalog) {
        cacheCatalog(catalog);
        return CompletableFuture.completedFuture(null);
    }

    public void cacheCatalog(CachedBasketCatalog catalog) {
        if (!isUsable() || catalog == null) {
            return;
        }
        String key = catalogKey();
        try {
            basketCatalogRedisTemplate.opsForValue().set(key, catalog, Duration.ofSeconds(ttlSeconds));
            log.info("Basket catalog L2 put key={} ttl={}s", key, ttlSeconds);
        } catch (Exception e) {
            log.warn("Basket catalog L2 put failed key={} — fail-open: {}", key, e.getMessage());
        }
    }

    public void evict() {
        if (!isUsable()) {
            return;
        }
        String key = catalogKey();
        try {
            basketCatalogRedisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Basket catalog L2 delete failed key={} — fail-open: {}", key, e.getMessage());
        }
    }

    private boolean isUsable() {
        return isRedisEnabled && basketCatalogRedisTemplate != null;
    }

    private String catalogKey() {
        return keyPrefix + "v1";
    }
}
