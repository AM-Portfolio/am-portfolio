package com.portfolio.redis.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.PortfolioAnalysis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class PortfolioAnalysisRedisService {
    
    private final RedisTemplate<String, PortfolioAnalysis> portfolioAnalysisRedisTemplate;

    @Value("${spring.data.redis.portfolio-mover.ttl}")
    private Integer portfolioMoverTtl;

    @Value("${spring.data.redis.portfolio-mover.key-prefix}")
    private String portfolioMoverKeyPrefix;

    public void cachePortfolioAnalysis(PortfolioAnalysis analysis, String portfolioId, String userId, TimeInterval interval) {
        String key = buildKey(portfolioId, userId, interval);
        try {
            // For short intervals, use the interval duration as TTL
            Duration ttl = interval != null && interval.getDuration() != null && 
                          interval.getDuration().compareTo(Duration.ofSeconds(portfolioMoverTtl)) < 0 
                          ? interval.getDuration() 
                          : Duration.ofSeconds(portfolioMoverTtl);
            
            portfolioAnalysisRedisTemplate.opsForValue().set(key, analysis, ttl);
            log.debug("Cached portfolio analysis for key: {} with TTL: {} seconds", key, ttl.getSeconds());
        } catch (Exception e) {
            log.error("Error caching portfolio analysis for key {}: {}", key, e.getMessage(), e);
        }
    }

    public Optional<PortfolioAnalysis> getLatestAnalysis(String portfolioId, String userId, TimeInterval interval) {
        String key = buildKey(portfolioId, userId, interval);
        try {
            PortfolioAnalysis analysis = portfolioAnalysisRedisTemplate.opsForValue().get(key);
            if (analysis != null) {
                // Check if the analysis is still fresh (within the interval duration)
                if (interval != null && interval.getDuration() != null) {
                    Instant cutoff = Instant.now().minus(interval.getDuration());
                    if (analysis.getLastUpdated().isAfter(cutoff)) {
                        log.debug("Found fresh portfolio analysis in cache for key: {}", key);
                        return Optional.of(analysis);
                    } else {
                        log.debug("Found stale portfolio analysis in cache for key: {}, deleting", key);
                        portfolioAnalysisRedisTemplate.delete(key);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error retrieving portfolio analysis from cache for key {}: {}", key, e.getMessage(), e);
        }
        return Optional.empty();
    }

    private String buildKey(String portfolioId, String userId, TimeInterval interval) {
        return portfolioMoverKeyPrefix + portfolioId + ":" + userId + ":" + 
               (interval != null ? interval.getCode() : "default");
    }
}
