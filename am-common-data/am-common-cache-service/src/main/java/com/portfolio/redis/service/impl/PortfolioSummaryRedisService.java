package com.portfolio.redis.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.portfolio.redis.model.TimeInterval;
import com.portfolio.redis.model.PortfolioSummaryV1;
import com.portfolio.redis.service.base.AbstractRedisService;
import com.portfolio.redis.util.RedisUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PortfolioSummaryRedisService extends AbstractRedisService<String, PortfolioSummaryV1> {

    @Value("${spring.data.redis.portfolio-summary.ttl}")
    private Integer portfolioSummaryTtl;

    @Value("${spring.data.redis.portfolio-summary.key-prefix}")
    private String portfolioSummaryKeyPrefix;

    public PortfolioSummaryRedisService(
            RedisTemplate<String, PortfolioSummaryV1> redisTemplate) {
        super(redisTemplate);
    }

    @Override
    protected String getServiceName() {
        return "PortfolioSummary";
    }

    @Override
    protected Duration getDefaultTtl() {
        return Duration.ofSeconds(portfolioSummaryTtl);
    }

    @Override
    protected String buildKey(Object... parts) {
        return RedisUtils.buildKey(portfolioSummaryKeyPrefix, parts);
    }

    @Async
    public CompletableFuture<Void> cachePortfolioSummary(PortfolioSummaryV1 summary, String userId, TimeInterval interval) {
        log.info("Starting async caching of portfolio summary - User: {}, Interval: {}", 
            userId, interval != null ? interval.getCode() : "null");
        
        return CompletableFuture.runAsync(() -> {
            String key = buildKey(userId, interval != null ? interval.getCode() : "all");
            Duration ttl = getEffectiveTtl(interval);
            
            set(key, summary, ttl);
            log.info("Successfully cached portfolio summary - User: {}, Key: {}, TTL: {} seconds", 
                userId, key, ttl.getSeconds());
        });
    }

    public Optional<PortfolioSummaryV1> getLatestSummary(String userId, TimeInterval interval) {
        log.info("Retrieving latest portfolio summary - User: {}, Interval: {}", 
            userId, interval != null ? interval.getCode() : "null");
            
        String key = buildKey(userId, interval != null ? interval.getCode() : "all");
        Optional<PortfolioSummaryV1> summary = get(key);
        
        if (summary.isPresent() && interval != null && interval.getDuration() != null) {
            // Check if the summary is still fresh (within the interval duration)
            Instant cutoff = Instant.now().minus(interval.getDuration());
            
            if (summary.get().getLastUpdated().toInstant(ZoneOffset.UTC).isAfter(cutoff)) {
                log.info("Found fresh portfolio summary in cache - User: {}, Key: {}, LastUpdated: {}", 
                    userId, key, summary.get().getLastUpdated());
                return summary;
            } else {
                log.info("Found stale portfolio summary in cache - User: {}, Key: {}, LastUpdated: {}, deleting", 
                    userId, key, summary.get().getLastUpdated());
                delete(key);
                return Optional.empty();
            }
        }
        
        return summary;
    }

    private Duration getEffectiveTtl(TimeInterval interval) {
        if (interval != null && interval.getDuration() != null) {
            Duration intervalDuration = interval.getDuration();
            Duration defaultDuration = getDefaultTtl();
            return intervalDuration.compareTo(defaultDuration) < 0 ? intervalDuration : defaultDuration;
        }
        return getDefaultTtl();
    }
}
