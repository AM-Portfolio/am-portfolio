package com.portfolio.redis.service;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import com.portfolio.model.analytics.intelligence.PortfolioIntelligenceResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Fail-open Redis cache for portfolio intelligence Overview responses (WS7).
 */
@Service
@Slf4j
public class PortfolioIntelligenceRedisService {

    @Value("${cache.redis.enabled:true}")
    private boolean isRedisEnabled;

    @Value("${spring.data.redis.portfolio-intel.key-prefix:portfolio:intel:v1:}")
    private String keyPrefix;

    @Value("${spring.data.redis.portfolio-intel.ttl:90}")
    private int ttlSeconds;

    private final RedisTemplate<String, PortfolioIntelligenceResponse> portfolioIntelligenceRedisTemplate;

    public PortfolioIntelligenceRedisService(
            @Nullable RedisTemplate<String, PortfolioIntelligenceResponse> portfolioIntelligenceRedisTemplate) {
        this.portfolioIntelligenceRedisTemplate = portfolioIntelligenceRedisTemplate;
    }

    public Optional<PortfolioIntelligenceResponse> get(String portfolioId) {
        if (!isUsable() || portfolioId == null || portfolioId.isBlank()) {
            return Optional.empty();
        }
        String key = buildKey(portfolioId);
        try {
            PortfolioIntelligenceResponse cached = portfolioIntelligenceRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.debug("Intel L2 hit key={}", key);
                return Optional.of(cached);
            }
        } catch (Exception e) {
            log.warn("Intel L2 get failed key={} — fail-open: {}", key, e.getMessage());
        }
        return Optional.empty();
    }

    public void put(String portfolioId, PortfolioIntelligenceResponse response) {
        if (!isUsable() || portfolioId == null || portfolioId.isBlank() || response == null) {
            return;
        }
        String key = buildKey(portfolioId);
        try {
            portfolioIntelligenceRedisTemplate.opsForValue().set(
                    key, response, Duration.ofSeconds(Math.max(1, ttlSeconds)));
            log.debug("Intel L2 put key={} ttl={}s", key, ttlSeconds);
        } catch (Exception e) {
            log.warn("Intel L2 put failed key={} — fail-open: {}", key, e.getMessage());
        }
    }

    private boolean isUsable() {
        return isRedisEnabled && portfolioIntelligenceRedisTemplate != null;
    }

    /** Stable key; Redis TTL alone invalidates stale entries. */
    private String buildKey(String portfolioId) {
        return keyPrefix + portfolioId;
    }
}
