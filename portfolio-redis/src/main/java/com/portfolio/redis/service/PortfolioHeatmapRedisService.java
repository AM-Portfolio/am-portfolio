package com.portfolio.redis.service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.portfolio.model.analytics.Heatmap;
import com.portfolio.model.analytics.request.TimeFrameRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class PortfolioHeatmapRedisService {
    private final RedisTemplate<String, Heatmap> portfolioHeatmapRedisTemplate;


    @Value("${spring.data.redis.portfolio-heatmap.key-prefix:portfolio:heatmap:}")
    private String portfolioHeatmapKeyPrefix;

    @Value("${spring.data.redis.portfolio-heatmap.ttl:900}")
    private Integer portfolioHeatmapTtl;

    @Async("taskExecutor")
    public CompletableFuture<Void> cacheHeatmap(Heatmap heatmap, String portfolioId, TimeFrameRequest interval) {
        log.info("Starting async caching of portfolio heatmap - Portfolio: {}, Interval: {}", 
            portfolioId, interval != null ? interval.getTimeFrame() : "null");
        
        String key = buildKey(portfolioId, interval);
        log.debug("Generated cache key: {} for portfolio: {}", key, portfolioId);
        
        try {
            Duration ttl = Duration.ofSeconds(portfolioHeatmapTtl);
            log.debug("Setting cache with TTL: {} seconds for key: {}", ttl.getSeconds(), key);
            portfolioHeatmapRedisTemplate.opsForValue().set(key, heatmap, ttl);
            log.info("Successfully cached portfolio heatmap - Portfolio: {}, Key: {}, TTL: {} seconds", 
                portfolioId, key, ttl.getSeconds());
        } catch (Exception e) {
            log.error("Error caching portfolio heatmap - Portfolio: {}, Key: {}, Error: {}", 
                portfolioId, key, e.getMessage(), e);
        }
        return CompletableFuture.completedFuture(null);
    }

    public Optional<Heatmap> getCachedHeatmap(String portfolioId, TimeFrameRequest interval) {
        log.info("Retrieving latest portfolio heatmap - Portfolio: {}, Interval: {}", 
            portfolioId, interval != null ? interval.getTimeFrame() : "null");
            
        String key = buildKey(portfolioId, interval);
        log.debug("Generated cache key: {} for portfolio: {}", key, portfolioId);
        
        try {
            Heatmap heatmap = portfolioHeatmapRedisTemplate.opsForValue().get(key);
            
            if (heatmap != null) {
                log.info("Found portfolio heatmap in cache - Portfolio: {}, Key: {}", portfolioId, key);
                return Optional.of(heatmap);
            } else {
                log.debug("No portfolio heatmap found in cache - Portfolio: {}, Key: {}", portfolioId, key);
            }
        } catch (Exception e) {
            log.error("Error retrieving portfolio heatmap from cache - Portfolio: {}, Key: {}, Error: {}", 
                portfolioId, key, e.getMessage(), e);
        }
        
        return Optional.empty();
    }

    private String buildKey(String portfolioId, TimeFrameRequest interval) {
        String intervalCode = interval != null && interval.getTimeFrame() != null ? interval.getTimeFrame().getValue() : "all";
        String key = portfolioHeatmapKeyPrefix + portfolioId + ":" + intervalCode;
        log.trace("Built cache key: {} for portfolio: {}, interval: {}", key, portfolioId, intervalCode);
        return key;
    }
}
