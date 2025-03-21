package com.portfolio.rediscache.service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.PortfolioHoldings;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class PortfolioHoldingsRedisService {
    
    private final RedisTemplate<String, PortfolioHoldings> portfolioHoldingsRedisTemplate;

    @Value("${spring.data.redis.portfolio-holdings.ttl}")
    private Integer portfolioHoldingTtl;

    @Value("${spring.data.redis.portfolio-holdings.key-prefix}")
    private String portfolioKeyPrefix;

    @Async
    public CompletableFuture<Void> cachePortfolioHoldings(PortfolioHoldings holdings, String userId, TimeInterval interval) {
        
        return CompletableFuture.runAsync(() -> {
            String key = buildKey(userId, interval);
        try {
            // For short intervals, use the interval duration as TTL
            Duration ttl = interval != null && interval.getDuration() != null && 
                          interval.getDuration().compareTo(Duration.ofSeconds(portfolioHoldingTtl)) < 0 
                          ? interval.getDuration() 
                          : Duration.ofSeconds(portfolioHoldingTtl);
            
            portfolioHoldingsRedisTemplate.opsForValue().set(key, holdings, ttl);
            log.debug("Cached portfolio holdings for key: {} with TTL: {} seconds", key, ttl.getSeconds());
        } catch (Exception e) {
            log.error("Error caching portfolio holdings for key {}: {}", key, e.getMessage(), e);
        }
    });
    }

    public Optional<PortfolioHoldings> getLatestHoldings(String userId, TimeInterval interval) {
        String key = buildKey(userId, interval);
        try {
            PortfolioHoldings holdings = portfolioHoldingsRedisTemplate.opsForValue().get(key);
            if (holdings != null) {
                // Check if the holdings are still fresh (within the interval duration)
                if (interval != null && interval.getDuration() != null) {
                    Instant cutoff = Instant.now().minus(interval.getDuration());
                    if (holdings.getLastUpdated().toInstant(ZoneOffset.UTC).isAfter(cutoff)) {
                        log.debug("Found fresh portfolio holdings in cache for key: {}", key);
                        return Optional.of(holdings);
                    } else {
                        log.debug("Found stale portfolio holdings in cache for key: {}, deleting", key);
                        portfolioHoldingsRedisTemplate.delete(key);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error retrieving portfolio holdings from cache for key {}: {}", key, e.getMessage(), e);
        }
        return Optional.empty();
    }

    private String buildKey(String userId, TimeInterval interval) {
        return portfolioKeyPrefix + userId + ":" + 
               (interval != null ? interval.getCode() : "default");
    }
}
