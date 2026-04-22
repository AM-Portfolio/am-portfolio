package com.portfolio.redis.util;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUtils {
    
    public static <T> void batchSet(RedisTemplate<String, T> redisTemplate, Map<String, T> keyValueMap, Duration ttl) {
        try {
            redisTemplate.opsForValue().multiSet(keyValueMap);
            keyValueMap.keySet().forEach(key -> 
                redisTemplate.expire(key, ttl.getSeconds(), TimeUnit.SECONDS));
            log.debug("Successfully batch set {} entries with TTL: {} seconds", keyValueMap.size(), ttl.getSeconds());
        } catch (Exception e) {
            log.error("Error during batch set operation: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to perform batch set operation", e);
        }
    }

    public static <T> void deleteByPattern(RedisTemplate<String, T> redisTemplate, String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Successfully deleted {} keys matching pattern: {}", keys.size(), pattern);
            }
        } catch (Exception e) {
            log.error("Error deleting keys with pattern {}: {}", pattern, e.getMessage(), e);
            throw new RuntimeException("Failed to delete keys by pattern", e);
        }
    }

    public static <T> void setWithTtl(RedisTemplate<String, T> redisTemplate, String key, T value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
            log.debug("Successfully set key: {} with TTL: {} seconds", key, ttl.getSeconds());
        } catch (Exception e) {
            log.error("Error setting key {} with TTL: {}", key, e.getMessage(), e);
            throw new RuntimeException("Failed to set value with TTL", e);
        }
    }

    public static String buildKey(String prefix, Object... parts) {
        StringBuilder key = new StringBuilder(prefix);
        for (Object part : parts) {
            if (part != null) {
                key.append(String.valueOf(part)).append(":");
            }
        }
        // Remove trailing colon if present
        if (key.charAt(key.length() - 1) == ':') {
            key.setLength(key.length() - 1);
        }
        return key.toString();
    }
}
