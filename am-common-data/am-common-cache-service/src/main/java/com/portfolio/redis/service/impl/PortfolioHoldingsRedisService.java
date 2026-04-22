package com.portfolio.redis.service.impl;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.portfolio.redis.model.TimeInterval;
import com.portfolio.redis.model.PortfolioHoldings;
import com.portfolio.redis.service.base.AbstractRedisService;
import com.portfolio.redis.util.RedisUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PortfolioHoldingsRedisService extends AbstractRedisService<String, PortfolioHoldings> {
    
    @Value("${spring.data.redis.portfolio-holdings.ttl}")
    private Integer portfolioHoldingsTtl;

    @Value("${spring.data.redis.portfolio-holdings.key-prefix}")
    private String portfolioHoldingsKeyPrefix;

    public PortfolioHoldingsRedisService(
            RedisTemplate<String, PortfolioHoldings> redisTemplate) {
        super(redisTemplate);
    }

    @Override
    protected String getServiceName() {
        return "PortfolioHoldings";
    }

    @Override
    protected Duration getDefaultTtl() {
        return Duration.ofSeconds(portfolioHoldingsTtl);
    }

    @Override
    protected String buildKey(Object... parts) {
        return RedisUtils.buildKey(portfolioHoldingsKeyPrefix, parts);
    }

    public void cachePortfolioHoldings(PortfolioHoldings holdings, String userId, TimeInterval interval) {
        String key = buildKey(userId, interval != null ? interval.getCode() : "default");
        Duration ttl = getEffectiveTtl(interval);
        set(key, holdings, ttl);
    }

    public Optional<PortfolioHoldings> getLatestHoldings(String userId, TimeInterval interval) {
        String key = buildKey(userId, interval != null ? interval.getCode() : "default");
        return get(key);
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
