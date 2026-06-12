package com.portfolio.redis.service;

import java.time.Duration;
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

    @Async("taskExecutor")
    public CompletableFuture<Void> cachePortfolioHoldings(PortfolioHoldings holdings, String userId, TimeInterval interval) {
        return cachePortfolioHoldings(holdings, userId, interval, null);
    }

    @Async("taskExecutor")
    public CompletableFuture<Void> cachePortfolioHoldings(PortfolioHoldings holdings, String userId, TimeInterval interval, String portfolioId) {
        String key = buildKey(userId, interval, portfolioId);
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
        return CompletableFuture.completedFuture(null);
    }

    public Optional<PortfolioHoldings> getLatestHoldings(String userId, TimeInterval interval) {
        return getLatestHoldings(userId, interval, null);
    }

    public Optional<PortfolioHoldings> getLatestHoldings(String userId, TimeInterval interval, String portfolioId) {
        String key = buildKey(userId, interval, portfolioId);
        try {
            PortfolioHoldings holdings = portfolioHoldingsRedisTemplate.opsForValue().get(key);
            if (holdings != null) {
                log.debug("Found portfolio holdings in cache for key: {}", key);
                return Optional.of(holdings);
            }
        } catch (Exception e) {
            log.error("Error retrieving portfolio holdings from cache for key {}: {}", key, e.getMessage(), e);
        }
        return Optional.empty();
    }

    private String buildKey(String userId, TimeInterval interval, String portfolioId) {
        String intervalCode = interval != null ? interval.getCode() : "default";
        String portPart = (portfolioId != null && !portfolioId.isEmpty()) ? portfolioId : "all";
        return portfolioKeyPrefix + userId + ":" + portPart + ":" + intervalCode;
    }
}
