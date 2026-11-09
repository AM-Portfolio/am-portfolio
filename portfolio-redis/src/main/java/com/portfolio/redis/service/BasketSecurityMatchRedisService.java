package com.portfolio.redis.service;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.portfolio.model.basket.cache.CachedSecurityMatch;

import lombok.extern.slf4j.Slf4j;

/**
 * L2 Redis cache for security name → ISIN matches used by basket enrichment. Fail-open.
 */
@Service
@Slf4j
public class BasketSecurityMatchRedisService {

    @Value("${cache.redis.enabled:true}")
    private boolean isRedisEnabled;

    @Value("${spring.data.redis.basket-secmatch.key-prefix:basket:secmatch:}")
    private String keyPrefix;

    @Value("${spring.data.redis.basket-secmatch.ttl:21600}")
    private Integer ttlSeconds;

    @Value("${BATCH_SEARCH_CACHE_ENABLED:true}")
    private boolean batchSearchCacheEnabled;

    private final RedisTemplate<String, CachedSecurityMatch> basketSecurityMatchRedisTemplate;

    public BasketSecurityMatchRedisService(
            @Nullable RedisTemplate<String, CachedSecurityMatch> basketSecurityMatchRedisTemplate) {
        this.basketSecurityMatchRedisTemplate = basketSecurityMatchRedisTemplate;
    }

    public Map<String, CachedSecurityMatch> getMatches(List<String> queries) {
        if (!isUsable() || queries == null || queries.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, CachedSecurityMatch> hits = new HashMap<>();
        try {
            List<String> keys = queries.stream().map(this::buildKey).collect(Collectors.toList());
            List<CachedSecurityMatch> values = basketSecurityMatchRedisTemplate.opsForValue().multiGet(keys);
            if (values == null) {
                return Collections.emptyMap();
            }
            for (int i = 0; i < queries.size(); i++) {
                CachedSecurityMatch match = values.get(i);
                if (match != null) {
                    hits.put(queries.get(i), match);
                }
            }
            if (!hits.isEmpty()) {
                log.info("Basket secmatch L2 hits={}/{}", hits.size(), queries.size());
            }
        } catch (Exception e) {
            log.warn("Basket secmatch L2 multiGet failed — fail-open: {}", e.getMessage());
        }
        return hits;
    }

    @Async("taskExecutor")
    public CompletableFuture<Void> cacheMatchesAsync(Map<String, CachedSecurityMatch> matches) {
        cacheMatches(matches);
        return CompletableFuture.completedFuture(null);
    }

    public void cacheMatches(Map<String, CachedSecurityMatch> matches) {
        if (!isUsable() || matches == null || matches.isEmpty()) {
            return;
        }
        try {
            Duration ttl = Duration.ofSeconds(ttlSeconds);
            for (Map.Entry<String, CachedSecurityMatch> entry : matches.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                basketSecurityMatchRedisTemplate.opsForValue().set(buildKey(entry.getKey()), entry.getValue(), ttl);
            }
            log.info("Basket secmatch L2 put count={} ttl={}s", matches.size(), ttlSeconds);
        } catch (Exception e) {
            log.warn("Basket secmatch L2 put failed — fail-open: {}", e.getMessage());
        }
    }

    private boolean isUsable() {
        return isRedisEnabled && batchSearchCacheEnabled && basketSecurityMatchRedisTemplate != null;
    }

    private String buildKey(String query) {
        return keyPrefix + query.trim().toLowerCase(Locale.ROOT);
    }
}
