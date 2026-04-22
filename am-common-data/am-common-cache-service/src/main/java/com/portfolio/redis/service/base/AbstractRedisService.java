package com.portfolio.redis.service.base;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;

import com.portfolio.redis.exception.RedisOperationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractRedisService<K, V> implements RedisOperations<K, V> {

    protected final RedisTemplate<K, V> redisTemplate;

    protected abstract String getServiceName();
    protected abstract Duration getDefaultTtl();
    protected abstract K buildKey(Object... parts);

    @Override
    public void set(K key, V value, Duration ttl) {
        String operation = getServiceName() + ".set";
        
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
            log.debug("[{}] Successfully set value for key: {} with TTL: {}", getServiceName(), key, ttl);
        } catch (Exception e) {
            log.error("[{}] Error setting value for key {}: {}", getServiceName(), key, e.getMessage(), e);
            throw new RedisOperationException("Failed to set value in Redis", e);
        } 
    }

    @Override
    public void setBatch(Map<K, V> entries, Duration ttl) {
        String operation = getServiceName() + ".setBatch";
        
        try {
            redisTemplate.opsForValue().multiSet(entries);
            entries.keySet().forEach(key -> 
                redisTemplate.expire(key, ttl.getSeconds(), TimeUnit.SECONDS));
            log.debug("[{}] Successfully set batch of {} entries", getServiceName(), entries.size());
        } catch (Exception e) {
            log.error("[{}] Error setting batch entries: {}", getServiceName(), e.getMessage(), e);
            throw new RedisOperationException("Failed to set batch entries in Redis", e);
        }
    }

    @Override
    public Optional<V> get(K key) {
        String operation = getServiceName() + ".get";
        
        try {
            V value = redisTemplate.opsForValue().get(key);
                
            if (value != null) {
                log.debug("[{}] Cache hit for key: {}", getServiceName(), key);
                return Optional.of(value);
            } else {
                log.debug("[{}] Cache miss for key: {}", getServiceName(), key);
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("[{}] Error retrieving value for key {}: {}", getServiceName(), key, e.getMessage(), e);
            throw new RedisOperationException("Failed to get value from Redis", e);
        }
    }

    @Override
    public List<V> getAll(List<K> keys) {
        String operation = getServiceName() + ".getAll";
        
        try {
            return redisTemplate.opsForValue().multiGet(keys);
        } catch (Exception e) {
            log.error("[{}] Error retrieving multiple values: {}", getServiceName(), e.getMessage(), e);
            throw new RedisOperationException("Failed to get multiple values from Redis", e);
        }
    }

    @Override
    public void delete(K key) {
        String operation = getServiceName() + ".delete";
        
        try {
            redisTemplate.delete(key);
            log.debug("[{}] Successfully deleted key: {}", getServiceName(), key);
        } catch (Exception e) {
            log.error("[{}] Error deleting key {}: {}", getServiceName(), key, e.getMessage(), e);
            throw new RedisOperationException("Failed to delete key from Redis", e);
        }
    }

    @Override
    public void deleteBatch(List<K> keys) {
        String operation = getServiceName() + ".deleteBatch";
        
        try {
            redisTemplate.delete(keys);
            log.debug("[{}] Successfully deleted {} keys", getServiceName(), keys.size());
        } catch (Exception e) {
            log.error("[{}] Error deleting batch of keys: {}", getServiceName(), e.getMessage(), e);
            throw new RedisOperationException("Failed to delete batch of keys from Redis", e);
        }
    }

    @Override
    public boolean exists(K key) {
        String operation = getServiceName() + ".exists";
        
        try {
            return redisTemplate.hasKey(key);
        } catch (Exception e) {
            log.error("[{}] Error checking existence of key {}: {}", getServiceName(), key, e.getMessage(), e);
            throw new RedisOperationException("Failed to check key existence in Redis", e);
        }
    }

    @Override
    public Duration getTimeToLive(K key) {
        String operation = getServiceName() + ".getTimeToLive";
        
        try {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            return ttl != null && ttl > 0 ? Duration.ofSeconds(ttl) : Duration.ZERO;
        } catch (Exception e) {
            log.error("[{}] Error getting TTL for key {}: {}", getServiceName(), key, e.getMessage(), e);
            throw new RedisOperationException("Failed to get TTL from Redis", e);
        }
    }

    protected Duration getEffectiveTtl(Duration requestedTtl) {
        return requestedTtl != null ? requestedTtl : getDefaultTtl();
    }
}
