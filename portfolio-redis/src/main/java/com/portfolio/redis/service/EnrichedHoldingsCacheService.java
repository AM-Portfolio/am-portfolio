package com.portfolio.redis.service;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.model.portfolio.PortfolioHoldings;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class EnrichedHoldingsCacheService {

    private final RedisTemplate<String, PortfolioHoldings> portfolioHoldingsRedisTemplate;
    
    // Shared TTL for Enriched Holdings - 15 minutes
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);
    
    // Lock TTL - maximum time a thread holds the lock to compute holdings
    private static final Duration LOCK_TTL = Duration.ofSeconds(45);

    /**
     * Gets enriched holdings from the shared cache, or computes them if missing.
     * Uses a SETNX lock to prevent cache stampedes when multiple requests hit at the same time.
     * 
     * @param userId The user ID
     * @param portfolioId The specific portfolio ID, or "all"
     * @param computeHoldings Supplier that fetches raw data and enriches it
     * @return List of enriched EquityHoldings
     */
    public List<EquityHoldings> getOrComputeEnrichedHoldings(String userId, String portfolioId, Supplier<List<EquityHoldings>> computeHoldings) {
        String key = "portfolio:enriched:" + userId + ":" + (portfolioId != null ? portfolioId : "all");
        String lockKey = key + ":lock";

        // 1. Try fast path - cache hit
        PortfolioHoldings cached = portfolioHoldingsRedisTemplate.opsForValue().get(key);
        if (cached != null && cached.getEquityHoldings() != null) {
            log.debug("Shared EnrichedHoldings cache hit for key: {}", key);
            return cached.getEquityHoldings();
        }

        // 2. Acquire lock - only one thread computes
        Boolean locked = portfolioHoldingsRedisTemplate.opsForValue().setIfAbsent(lockKey, PortfolioHoldings.builder().build(), LOCK_TTL);
        
        if (Boolean.TRUE.equals(locked)) {
            log.info("Acquired lock to enrich holdings for key: {}", key);
            try {
                // 3. Double-check after acquiring lock (another thread may have populated it while we waited)
                cached = portfolioHoldingsRedisTemplate.opsForValue().get(key);
                if (cached != null && cached.getEquityHoldings() != null) {
                    log.info("Found populated cache after acquiring lock for key: {}", key);
                    return cached.getEquityHoldings();
                }

                // 4. Actually compute
                log.info("Computing enriched holdings for key: {}", key);
                List<EquityHoldings> enriched = computeHoldings.get();
                
                // Wrap in PortfolioHoldings to use the existing RedisTemplate
                PortfolioHoldings toCache = PortfolioHoldings.builder()
                        .userId(userId)
                        .portfolioId(portfolioId)
                        .equityHoldings(enriched)
                        .lastUpdated(java.time.LocalDateTime.now())
                        .build();
                        
                portfolioHoldingsRedisTemplate.opsForValue().set(key, toCache, CACHE_TTL);
                return enriched;
            } finally {
                // Release lock
                portfolioHoldingsRedisTemplate.delete(lockKey);
            }
        } else {
            // 5. Another thread is computing - wait and poll
            log.info("Another thread is enriching holdings for key: {}. Waiting...", key);
            return waitForCacheOrFallback(key, computeHoldings);
        }
    }

    private List<EquityHoldings> waitForCacheOrFallback(String key, Supplier<List<EquityHoldings>> fallbackCompute) {
        int attempts = 0;
        int maxAttempts = 35; // wait up to 35 seconds
        
        while (attempts < maxAttempts) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Thread interrupted while waiting for cache lock on key: {}", key);
                break;
            }
            
            PortfolioHoldings cached = portfolioHoldingsRedisTemplate.opsForValue().get(key);
            if (cached != null && cached.getEquityHoldings() != null) {
                log.info("Cache populated by another thread for key: {}", key);
                return cached.getEquityHoldings();
            }
            attempts++;
        }
        
        log.warn("Timed out waiting for cache lock on key: {}. Falling back to inline computation.", key);
        return fallbackCompute.get();
    }
    
    /**
     * Evicts the shared cache when a holding is added/updated/deleted.
     */
    public void evictEnrichedHoldingsCache(String userId, String portfolioId) {
        String key = "portfolio:enriched:" + userId + ":" + (portfolioId != null ? portfolioId : "all");
        portfolioHoldingsRedisTemplate.delete(key);
        log.info("Evicted shared EnrichedHoldings cache for key: {}", key);
    }
}
