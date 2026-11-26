package com.portfolio.redis.service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.portfolio.model.analytics.Heatmap;
import com.portfolio.model.analytics.request.TimeFrameRequest;
import com.portfolio.redis.session.CashSessionClock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class PortfolioHeatmapRedisService {
    
    @org.springframework.beans.factory.annotation.Value("${cache.redis.enabled:true}")
    private boolean isRedisEnabled;
    private final RedisTemplate<String, Heatmap> portfolioHeatmapRedisTemplate;
    private final ObjectProvider<CashSessionClock> cashSessionClock;

    @Value("${spring.data.redis.portfolio-heatmap.key-prefix:portfolio:heatmap:}")
    private String portfolioHeatmapKeyPrefix;

    @Value("${spring.data.redis.portfolio-heatmap.ttl:900}")
    private Integer portfolioHeatmapTtl;

    /** Cash-hours mosaic TTL so tile % tracks quote TTL (~2 min), not 15 min. */
    @Value("${spring.data.redis.portfolio-heatmap.cash-hours-ttl:90}")
    private Integer portfolioHeatmapCashHoursTtl;

    @Async("taskExecutor")
    public CompletableFuture<Void> cacheHeatmap(Heatmap heatmap, String portfolioId, TimeFrameRequest interval) {
        if (!isRedisEnabled) return java.util.concurrent.CompletableFuture.completedFuture(null);
        if (heatmap == null || heatmap.getSectors() == null || heatmap.getSectors().isEmpty()) {
            log.debug("Skipping cache of empty heatmap for portfolio: {}", portfolioId);
            return CompletableFuture.completedFuture(null);
        }
        log.info("Starting async caching of portfolio heatmap - Portfolio: {}, Interval: {}", 
            portfolioId, interval != null ? interval.getTimeFrame() : "null");
        
        String key = buildKey(portfolioId, interval);
        log.debug("Generated cache key: {} for portfolio: {}", key, portfolioId);
        
        try {
            Duration ttl = resolveTtl();
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

    Duration resolveTtl() {
        if (isCashOpen()) {
            int cashTtl = portfolioHeatmapCashHoursTtl != null && portfolioHeatmapCashHoursTtl > 0
                    ? portfolioHeatmapCashHoursTtl
                    : 90;
            return Duration.ofSeconds(cashTtl);
        }
        int offHours = portfolioHeatmapTtl != null && portfolioHeatmapTtl > 0 ? portfolioHeatmapTtl : 900;
        return Duration.ofSeconds(offHours);
    }

    private boolean isCashOpen() {
        CashSessionClock clock = cashSessionClock != null ? cashSessionClock.getIfAvailable() : null;
        if (clock != null) {
            return clock.isCashOpen();
        }
        return PortfolioMarketDataRedisService.isCashMarketHours(
                java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
    }

    public Optional<Heatmap> getCachedHeatmap(String portfolioId, TimeFrameRequest interval) {
        if (!isRedisEnabled) return java.util.Optional.empty();
        log.info("Retrieving latest portfolio heatmap - Portfolio: {}, Interval: {}", 
            portfolioId, interval != null ? interval.getTimeFrame() : "null");
            
        String key = buildKey(portfolioId, interval);
        log.debug("Generated cache key: {} for portfolio: {}", key, portfolioId);
        
        try {
            Heatmap heatmap = portfolioHeatmapRedisTemplate.opsForValue().get(key);
            
            if (heatmap != null && heatmap.getSectors() != null && !heatmap.getSectors().isEmpty()) {
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
        String fromDate = interval != null && interval.getFromDate() != null ? interval.getFromDate().toString() : "null";
        String toDate = interval != null && interval.getToDate() != null ? interval.getToDate().toString() : "null";
        String scope = "ALL";
        if (interval instanceof com.portfolio.model.analytics.request.AdvancedAnalyticsRequest advanced) {
            String raw = advanced.getAssetScope();
            if (raw != null && !raw.isBlank()) {
                scope = raw.trim().toUpperCase(java.util.Locale.ROOT);
            }
        }
        // sec3: invalidate pre-cash-hours-ttl mosaics (sec2 used 900s hard TTL).
        String key = portfolioHeatmapKeyPrefix + "sec3:" + portfolioId + ":" + intervalCode + ":" + fromDate + ":" + toDate
                + ":scope:" + scope;
        log.trace("Built cache key: {} for portfolio: {}, interval: {}, from: {}, to: {}, scope: {}",
                key, portfolioId, intervalCode, fromDate, toDate, scope);
        return key;
    }
}

