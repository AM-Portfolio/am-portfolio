package com.portfolio.redis.service;

import java.util.Collections;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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
    
    
    @org.springframework.beans.factory.annotation.Value("${cache.redis.enabled:true}")
    private boolean isRedisEnabled;
private final RedisTemplate<String, PortfolioHoldings> portfolioHoldingsRedisTemplate;

    @Value("${spring.data.redis.portfolio-holdings.ttl}")
    private Integer portfolioHoldingTtl;

    @Value("${spring.data.redis.portfolio-holdings.key-prefix}")
    private String portfolioKeyPrefix;

    @Async("taskExecutor")
    public CompletableFuture<Void> cachePortfolioHoldings(PortfolioHoldings holdings, String userId, TimeInterval interval) {
        if (!isRedisEnabled) return java.util.concurrent.CompletableFuture.completedFuture(null);
        return cachePortfolioHoldings(holdings, userId, interval, null);
    }

    @Async("taskExecutor")
    public CompletableFuture<Void> cachePortfolioHoldings(PortfolioHoldings holdings, String userId, TimeInterval interval, String portfolioId) {
        if (!isRedisEnabled) return java.util.concurrent.CompletableFuture.completedFuture(null);
        String key = buildKey(userId, interval, portfolioId);
        try {
            Duration ttl = computeHoldingsTtl(interval);
            
            portfolioHoldingsRedisTemplate.opsForValue().set(key, holdings, ttl);
            log.debug("Cached portfolio holdings for key: {} with TTL: {} seconds", key, ttl.getSeconds());
        } catch (Exception e) {
            log.error("Error caching portfolio holdings for key {}: {}", key, e.getMessage(), e);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Cash hours (IST 09:15–15:30 weekdays): short TTL so structure refresh is frequent;
     * prices are always overlaid from mktdata on read. Off hours: longer TTL.
     */
    Duration computeHoldingsTtl(TimeInterval interval) {
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        ZonedDateTime now = ZonedDateTime.now(ist);
        LocalTime t = now.toLocalTime();
        DayOfWeek day = now.getDayOfWeek();
        boolean cashOpen = day.getValue() < 6
                && !t.isBefore(LocalTime.of(9, 15))
                && t.isBefore(LocalTime.of(15, 30));
        if (cashOpen) {
            return Duration.ofSeconds(90);
        }
        if (interval != null && interval.getDuration() != null
                && interval.getDuration().compareTo(Duration.ofSeconds(portfolioHoldingTtl)) < 0) {
            return interval.getDuration();
        }
        int offHours = portfolioHoldingTtl != null && portfolioHoldingTtl > 0 ? portfolioHoldingTtl : 900;
        return Duration.ofSeconds(Math.min(offHours, 1800));
    }

    public Optional<PortfolioHoldings> getLatestHoldings(String userId, TimeInterval interval) {
        if (!isRedisEnabled) return java.util.Optional.empty();
        return getLatestHoldings(userId, interval, null);
    }

    public Optional<PortfolioHoldings> getLatestHoldings(String userId, TimeInterval interval, String portfolioId) {
        if (!isRedisEnabled) return java.util.Optional.empty();
        String key = buildKey(userId, interval, portfolioId);
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
                } else {
                    log.debug("Found portfolio holdings in cache for key: {} (relying on TTL)", key);
                    return Optional.of(holdings);
                }
            }
        } catch (Exception e) {
            log.error("Error retrieving portfolio holdings from cache for key {}: {}", key, e.getMessage(), e);
        }
        return Optional.empty();
    }

    private String buildKey(String userId, TimeInterval interval, String portfolioId) {
        String intervalCode = interval != null ? interval.getCode() : "default";
        String portPart = (portfolioId != null && !portfolioId.trim().isEmpty()) ? portfolioId : "all";
        return portfolioKeyPrefix + userId + ":" + portPart + ":" + intervalCode;
    }

    public void evictPortfolioHoldings(String userId, String portfolioId) {
        if (!isRedisEnabled) return;
        try {
            // Need to clear all intervals for this portfolio
            for (TimeInterval interval : TimeInterval.values()) {
                String key = buildKey(userId, interval, portfolioId);
                portfolioHoldingsRedisTemplate.delete(key);
                log.debug("Evicted portfolio holdings cache for key: {}", key);
                
                // Also clear the "all" portfolios cache since it includes this one
                String allKey = buildKey(userId, interval, null);
                portfolioHoldingsRedisTemplate.delete(allKey);
                log.debug("Evicted 'all' portfolio holdings cache for key: {}", allKey);
            }
        } catch (Exception e) {
            log.error("Error evicting portfolio holdings cache for user {} portfolio {}: {}", userId, portfolioId, e.getMessage());
        }
    }
}

