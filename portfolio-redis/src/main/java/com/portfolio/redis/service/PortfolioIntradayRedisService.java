package com.portfolio.redis.service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.portfolio.model.portfolio.IntradayDataPoint;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class PortfolioIntradayRedisService {
    private final RedisTemplate<String, IntradayDataPoint[]> portfolioIntradayRedisTemplate;

    private static final String KEY_PREFIX = "portfolio:intraday:v2:";
    private static final int MARKET_HOURS_TTL_MINUTES = 5;
    private static final int OFF_MARKET_HOURS_TTL_HOURS = 24;

    private String buildKey(String userId, String portfolioId) {
        String safePortfolioId = (portfolioId != null && !portfolioId.trim().isEmpty()) ? portfolioId : "all";
        return KEY_PREFIX + userId + ":" + safePortfolioId;
    }

    public void cacheIntradayData(String userId, String portfolioId, List<IntradayDataPoint> data, boolean isMarketOpen) {
        String key = buildKey(userId, portfolioId);
        try {
            Duration ttl = isMarketOpen ? Duration.ofMinutes(MARKET_HOURS_TTL_MINUTES) : Duration.ofHours(OFF_MARKET_HOURS_TTL_HOURS);
            log.debug("Caching intraday data for key: {} with TTL: {} {}", key, 
                      isMarketOpen ? MARKET_HOURS_TTL_MINUTES : OFF_MARKET_HOURS_TTL_HOURS, 
                      isMarketOpen ? "minutes" : "hours");
            
            IntradayDataPoint[] array = data.toArray(new IntradayDataPoint[0]);
            portfolioIntradayRedisTemplate.opsForValue().set(key, array, ttl);
        } catch (Exception e) {
            log.error("Failed to cache intraday data for key: {}", key, e);
        }
    }

    public Optional<List<IntradayDataPoint>> getIntradayData(String userId, String portfolioId) {
        String key = buildKey(userId, portfolioId);
        try {
            IntradayDataPoint[] array = portfolioIntradayRedisTemplate.opsForValue().get(key);
            if (array != null) {
                log.debug("Cache hit for intraday data key: {}", key);
                return Optional.of(Arrays.asList(array));
            }
        } catch (Exception e) {
            log.error("Failed to fetch intraday data from cache for key: {}", key, e);
        }
        return Optional.empty();
    }
}
