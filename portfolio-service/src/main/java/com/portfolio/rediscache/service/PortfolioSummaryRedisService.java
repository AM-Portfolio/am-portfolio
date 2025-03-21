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
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class PortfolioSummaryRedisService {
    private final RedisTemplate<String, PortfolioSummaryV1> portfolioSummaryRedisTemplate;

    @Value("${spring.data.redis.portfolio-summary.ttl}")
    private Integer portfolioSummaryTtl;

    @Value("${spring.data.redis.portfolio-summary.key-prefix}")
    private String portfolioSummaryKeyPrefix;

    @Async
    public CompletableFuture<Void> cachePortfolioSummary(PortfolioSummaryV1 summary, String userId, TimeInterval interval) {
        log.info("Starting async caching of portfolio summary - User: {}, Interval: {}", 
            userId, interval != null ? interval.getCode() : "null");
        
        return CompletableFuture.runAsync(() -> {
            String key = buildKey(userId, interval);
            log.debug("Generated cache key: {} for user: {}", key, userId);
            
            try {
                // For short intervals, use the interval duration as TTL
                Duration ttl = interval != null && interval.getDuration() != null && 
                              interval.getDuration().compareTo(Duration.ofSeconds(portfolioSummaryTtl)) < 0 
                              ? interval.getDuration() 
                              : Duration.ofSeconds(portfolioSummaryTtl);
                
                log.debug("Setting cache with TTL: {} seconds for key: {}", ttl.getSeconds(), key);
                portfolioSummaryRedisTemplate.opsForValue().set(key, summary, ttl);
                log.info("Successfully cached portfolio summary - User: {}, Key: {}, TTL: {} seconds", 
                    userId, key, ttl.getSeconds());
            } catch (Exception e) {
                log.error("Error caching portfolio summary - User: {}, Key: {}, Error: {}", 
                    userId, key, e.getMessage(), e);
            }
        });
    }

    public Optional<PortfolioSummaryV1> getLatestSummary(String userId, TimeInterval interval) {
        log.info("Retrieving latest portfolio summary - User: {}, Interval: {}", 
            userId, interval != null ? interval.getCode() : "null");
            
        String key = buildKey(userId, interval);
        log.debug("Generated cache key: {} for user: {}", key, userId);
        
        try {
            PortfolioSummaryV1 summary = portfolioSummaryRedisTemplate.opsForValue().get(key);
            
            if (summary != null) {
                log.debug("Found portfolio summary in cache - User: {}, Key: {}", userId, key);
                
                // Check if the summary is still fresh (within the interval duration)
                if (interval != null && interval.getDuration() != null) {
                    Instant cutoff = Instant.now().minus(interval.getDuration());
                    
                    if (summary.getLastUpdated().toInstant(ZoneOffset.UTC).isAfter(cutoff)) {
                        log.info("Found fresh portfolio summary in cache - User: {}, Key: {}, LastUpdated: {}", 
                            userId, key, summary.getLastUpdated());
                        return Optional.of(summary);
                    } else {
                        log.info("Found stale portfolio summary in cache - User: {}, Key: {}, LastUpdated: {}, deleting", 
                            userId, key, summary.getLastUpdated());
                        portfolioSummaryRedisTemplate.delete(key);
                    }
                } else {
                    log.info("Found portfolio summary in cache with no interval constraint - User: {}, Key: {}", 
                        userId, key);
                    return Optional.of(summary);
                }
            } else {
                log.debug("No portfolio summary found in cache - User: {}, Key: {}", userId, key);
            }
        } catch (Exception e) {
            log.error("Error retrieving portfolio summary from cache - User: {}, Key: {}, Error: {}", 
                userId, key, e.getMessage(), e);
        }
        
        return Optional.empty();
    }

    private String buildKey(String userId, TimeInterval interval) {
        String intervalCode = interval != null ? interval.getCode() : "all";
        String key = portfolioSummaryKeyPrefix + userId + ":" + intervalCode;
        log.trace("Built cache key: {} for user: {}, interval: {}", key, userId, intervalCode);
        return key;
    }
}
