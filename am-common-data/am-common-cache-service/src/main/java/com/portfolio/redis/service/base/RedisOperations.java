package com.portfolio.redis.service.base;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface RedisOperations<K, V> {
    void set(K key, V value, Duration ttl);
    void setBatch(Map<K, V> entries, Duration ttl);
    Optional<V> get(K key);
    List<V> getAll(List<K> keys);
    void delete(K key);
    void deleteBatch(List<K> keys);
    boolean exists(K key);
    Duration getTimeToLive(K key);
}
