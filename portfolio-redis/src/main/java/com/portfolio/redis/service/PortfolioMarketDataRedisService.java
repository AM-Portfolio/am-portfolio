package com.portfolio.redis.service;

import java.util.Collections;
import com.portfolio.model.market.MarketData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import io.micrometer.observation.annotation.Observed;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioMarketDataRedisService {

    
    @org.springframework.beans.factory.annotation.Value("${cache.redis.enabled:true}")
    private boolean isRedisEnabled;
private final RedisTemplate<String, MarketData> portfolioMarketDataRedisTemplate;

    @Value("${spring.data.redis.portfolio-market-data.key-prefix:portfolio:mktdata:}")
    private String keyPrefix;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime MARKET_OPEN  = LocalTime.of(9, 15);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

    /**
     * Cache a batch of market data with smart TTL based on IST market hours.
     */
    @Observed(name = "redis.cache.market_data", contextualName = "cache-market-data-write")
    public void cacheMarketData(Map<String, MarketData> data) {
        if (!isRedisEnabled) return;
        if (data == null || data.isEmpty()) return;
        Duration ttl = computeSmartTtl();
        
        try {
            portfolioMarketDataRedisTemplate.executePipelined((org.springframework.data.redis.connection.RedisConnection connection) -> {
                data.forEach((symbol, marketData) -> {
                    try {
                        byte[] key = ((org.springframework.data.redis.serializer.RedisSerializer<String>) portfolioMarketDataRedisTemplate.getKeySerializer()).serialize(keyPrefix + symbol);
                        byte[] value = ((org.springframework.data.redis.serializer.RedisSerializer<MarketData>) portfolioMarketDataRedisTemplate.getValueSerializer()).serialize(marketData);
                        if (key != null && value != null) {
                            connection.setEx(key, ttl.getSeconds(), value);
                        }
                    } catch (Exception e) {
                        log.warn("[MktDataCache] Failed to serialize symbol {}: {}", symbol, e.getMessage());
                    }
                });
                return null;
            });
            log.info("[MktDataCache] Pipelined cache of {} symbols with TTL={}", data.size(), ttl);
        } catch (Exception e) {
            log.warn("[MktDataCache] Failed to execute pipeline cache: {}", e.getMessage());
        }
    }

    /**
     * Retrieve a batch of cached market data.
     */
    public Map<String, MarketData> getMarketData(List<String> symbols) {
        if (!isRedisEnabled) return Collections.emptyMap();

        if (symbols == null || symbols.isEmpty()) return Collections.emptyMap();
        
        List<String> keys = symbols.stream().map(s -> keyPrefix + s).collect(Collectors.toList());
        Map<String, MarketData> result = new HashMap<>();
        
        try {
            List<MarketData> values = portfolioMarketDataRedisTemplate.opsForValue().multiGet(keys);
            if (values != null) {
                for (int i = 0; i < symbols.size(); i++) {
                    if (values.get(i) != null) {
                        result.put(symbols.get(i), values.get(i));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[MktDataCache] Failed to read batch of symbols: {}", e.getMessage());
        }
        
        log.debug("[MktDataCache] multiGet hit {}/{} symbols", result.size(), symbols.size());
        return result;
    }

    /**
     * NSE cash session: weekday 09:15–15:30 IST. Same clock {@link #computeSmartTtl()} already used.
     * Last-trade Redis/Mongo is only valid in this window.
     */
    public boolean isCashMarketHours() {
        return isCashMarketHours(ZonedDateTime.now(IST));
    }

    public static boolean isCashMarketHours(ZonedDateTime nowIst) {
        if (nowIst == null) {
            return false;
        }
        DayOfWeek day = nowIst.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = nowIst.toLocalTime();
        return !time.isBefore(MARKET_OPEN) && time.isBefore(MARKET_CLOSE);
    }

    /**
     * Compute TTL based on IST day-of-week and current time.
     */
    public Duration computeSmartTtl() {
        ZonedDateTime nowIst = ZonedDateTime.now(IST);
        DayOfWeek day = nowIst.getDayOfWeek();
        LocalTime time = nowIst.toLocalTime();

        ZonedDateTime nextMarketOpen = resolveNextMarketOpen(nowIst, day, time);
        Duration ttl = Duration.between(nowIst, nextMarketOpen);

        // Short TTL while cash is open so holdings stay near dashboard quotes.
        // After close, keep until next open — caller must skip last-trade cache first.
        if (isCashMarketHours(nowIst)) {
            ttl = Duration.ofMinutes(2);
        } else if (ttl.toMinutes() < 5) {
            ttl = Duration.ofMinutes(5);
        }
        if (ttl.toHours()   > 80) ttl = Duration.ofHours(80);

        log.debug("[MktDataCache] SmartTTL={} (IST={}, day={})", ttl, nowIst.toLocalTime(), day);
        return ttl;
    }

    private ZonedDateTime resolveNextMarketOpen(ZonedDateTime now, DayOfWeek day, LocalTime time) {
        ZonedDateTime todayOpen = now.toLocalDate().atTime(MARKET_OPEN).atZone(IST);

        switch (day) {
            case SATURDAY:
                return todayOpen.plusDays(2);
            case SUNDAY:
                return todayOpen.plusDays(1);
            default: // Mon-Fri
                if (time.isBefore(MARKET_OPEN)) return todayOpen;
                if (day == DayOfWeek.FRIDAY) return todayOpen.plusDays(3);
                return todayOpen.plusDays(1);
        }
    }
}

