package com.portfolio.redis.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.am.common.investment.model.events.StockInsidicesEventData;
import com.portfolio.model.cache.StockIndicesEventDataCache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis service for caching and retrieving stock indices event data
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockIndicesEventRedisService {

    private final RedisTemplate<String, StockIndicesEventDataCache> stockIndicesRedisTemplate;
    private static final int BATCH_SIZE = 100;

    @Value("${spring.data.redis.stock-indices.ttl}")
    private Integer stockIndicesTtl;

    @Value("${spring.data.redis.stock-indices.key-prefix}")
    private String stockIndicesKeyPrefix;

    @Value("${spring.data.redis.stock-indices.historical.key-prefix}")
    private String historicalKeyPrefix;

    @Value("${spring.data.redis.stock-indices.historical.ttl}")
    private Integer historicalTtl;

    /**
     * Asynchronously cache a batch of stock indices event data
     * 
     * @param indicesEvents List of stock indices events to cache
     * @return CompletableFuture<Void> representing the completion of the caching operation
     */
    @Async
    public CompletableFuture<Void> cacheStockIndicesEventDataBatch(List<StockInsidicesEventData> indicesEvents) {
        return CompletableFuture.runAsync(() -> {
            if (indicesEvents == null || indicesEvents.isEmpty()) {
                log.warn("Received empty or null stock indices events batch");
                return;
            }
            
            log.info("Starting to process {} stock indices events", indicesEvents.size());
            try {
                // Process in chunks of BATCH_SIZE
                for (int i = 0; i < indicesEvents.size(); i += BATCH_SIZE) {
                    int end = Math.min(i + BATCH_SIZE, indicesEvents.size());
                    List<StockInsidicesEventData> batch = indicesEvents.subList(i, end);
                    log.debug("Processing batch {} to {} of {}", i, end, indicesEvents.size());
                    processBatch(batch);
                }
                log.info("Successfully processed all {} stock indices events", indicesEvents.size());
            } catch (Exception e) {
                log.error("Error processing stock indices event batch: {}", e.getMessage(), e);
                // Not rethrowing exception as per requirement to not acknowledge failures
            }
        });
    }

    /**
     * Process a batch of stock indices events
     * 
     * @param batch List of stock indices events to process
     */
    private void processBatch(List<StockInsidicesEventData> batch) {
        try {
            long startTime = System.currentTimeMillis();
            
            Map<String, StockIndicesEventDataCache> realtimeUpdates = batch.stream()
                .map(this::convertToStockIndicesEventDataCache)
                .peek(indices -> log.info("Creating realtime key for index: {}, value: {}", 
                    indices.getIndexSymbol(), indices.getIndexValue()))
                .collect(Collectors.toMap(
                    indices -> {
                        String key = stockIndicesKeyPrefix + indices.getIndexSymbol();
                        log.info("Generated realtime key: {}", key);
                        return key;
                    },
                    indices -> indices
                ));

            Map<String, StockIndicesEventDataCache> historicalUpdates = batch.stream()
                .map(this::convertToStockIndicesEventDataCache)
                .peek(indices -> log.info("Creating historical key for index: {}, timestamp: {}, value: {}", 
                    indices.getIndexSymbol(), indices.getTimestamp(), indices.getIndexValue()))
                .collect(Collectors.toMap(
                    indices -> {
                        String key = historicalKeyPrefix + indices.getIndexSymbol() + ":" + indices.getTimestamp().toString();
                        log.info("Generated historical key: {}", key);
                        return key;
                    },
                    indices -> indices
                ));

            try {
                log.info("Writing {} realtime updates to Redis", realtimeUpdates.size());
                stockIndicesRedisTemplate.opsForValue().multiSet(realtimeUpdates);
                
                // Set TTL for realtime keys
                realtimeUpdates.keySet().forEach(key -> {
                    boolean success = stockIndicesRedisTemplate.expire(key, Duration.ofSeconds(stockIndicesTtl)).booleanValue();
                    log.info("Set TTL for realtime key: {}, success: {}", key, success);
                });
                log.debug("Successfully cached realtime stock indices updates");
            } catch (Exception e) {
                log.error("Failed to cache realtime updates: {}", e.getMessage(), e);
            }

            try {
                log.info("Writing {} historical updates to Redis", historicalUpdates.size());
                stockIndicesRedisTemplate.opsForValue().multiSet(historicalUpdates);
                
                // Set TTL for historical keys
                historicalUpdates.keySet().forEach(key -> {
                    boolean success = stockIndicesRedisTemplate.expire(key, Duration.ofSeconds(historicalTtl)).booleanValue();
                    log.info("Set TTL for historical key: {}, success: {}", key, success);
                });
                log.debug("Successfully cached historical stock indices updates");
            } catch (Exception e) {
                log.error("Failed to cache historical updates: {}", e.getMessage(), e);
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Batch processing completed in {}ms for {} updates", duration, batch.size());
        } catch (Exception e) {
            log.error("Failed to process batch: {}", e.getMessage(), e);
        }
    }

    /**
     * Convert StockInsidicesEventData to StockIndicesEventDataCache
     * 
     * @param event StockInsidicesEventData to convert
     * @return StockIndicesEventDataCache
     */
    private StockIndicesEventDataCache convertToStockIndicesEventDataCache(StockInsidicesEventData event) {
        return StockIndicesEventDataCache.builder()
            .indexName(event.getName())
            // .indexSymbol(event.get())
            // .indexValue(event.getValue())
            // .previousClose(event.getPreviousClose())
            // .change(event.getChange())
            // .changePercent(event.getChangePercent())
            // .timestamp(event.getTimestamp())
            // .constituents(event.getConstituents())
            .build();
    }

    /**
     * Get the latest stock index data for a given symbol
     * 
     * @param symbol Index symbol to get data for
     * @return Optional<StockIndicesEventDataCache> containing the latest data if available
     */
    public Optional<StockIndicesEventDataCache> getLatestIndexData(String symbol) {
        try {
            String key = stockIndicesKeyPrefix + symbol;
            StockIndicesEventDataCache indexData = stockIndicesRedisTemplate.opsForValue().get(key);
            return Optional.ofNullable(indexData);
        } catch (Exception e) {
            log.error("Error retrieving index data for symbol: {}", symbol, e);
            return Optional.empty();
        }
    }

    /**
     * Get historical index data for a given symbol within a time range
     * 
     * @param symbol Index symbol to get data for
     * @param startTime Start time of the range
     * @param endTime End time of the range
     * @return List<StockIndicesEventDataCache> containing the historical data
     */
    public List<StockIndicesEventDataCache> getHistoricalIndexData(String symbol, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            String pattern = historicalKeyPrefix + symbol + ":*";
            List<StockIndicesEventDataCache> historicalData = new ArrayList<>();
            
            log.info("Searching with pattern: {} for time range: {} to {}", pattern, startTime, endTime);
            var keys = stockIndicesRedisTemplate.keys(pattern);
            log.info("Found {} keys matching pattern {}", keys.size(), pattern);
            
            // Sort keys to get most recent first
            List<String> sortedKeys = new ArrayList<>(keys);
            sortedKeys.sort((k1, k2) -> k2.compareTo(k1));  // Reverse order to get newest first
            
            for (String key : sortedKeys) {
                StockIndicesEventDataCache indexData = stockIndicesRedisTemplate.opsForValue().get(key);
                log.info("Key: {}, IndexData: {}", key, indexData);
                if (indexData != null && indexData.getTimestamp() != null) {
                    LocalDateTime timestamp = LocalDateTime.ofInstant(indexData.getTimestamp(), ZoneOffset.UTC);
                    if ((startTime == null || !timestamp.isBefore(startTime)) && 
                        (endTime == null || !timestamp.isAfter(endTime))) {
                        historicalData.add(indexData);
                        log.info("Added index data for key: {}, timestamp: {}", key, indexData.getTimestamp());
                    }
                } else {
                    log.warn("Null index data or timestamp for key: {}", key);
                }
            }

            log.info("Returning {} historical index data points for symbol: {}", historicalData.size(), symbol);
            return historicalData;
        } catch (Exception e) {
            log.error("Error retrieving historical index data for symbol: {}", symbol, e);
            return List.of();
        }
    }

    /**
     * Delete old index data for a given symbol before a specified time
     * 
     * @param symbol Index symbol to delete data for
     * @param beforeTime Time before which data should be deleted
     */
    public void deleteOldIndexData(String symbol, LocalDateTime beforeTime) {
        try {
            String pattern = historicalKeyPrefix + symbol + ":*";
            stockIndicesRedisTemplate.keys(pattern).stream()
                .map(key -> Map.entry(key, stockIndicesRedisTemplate.opsForValue().get(key)))
                .filter(entry -> entry.getValue() != null && 
                    entry.getValue().getTimestamp().isBefore(beforeTime.toInstant(ZoneOffset.UTC)))
                .forEach(entry -> stockIndicesRedisTemplate.delete(entry.getKey()));
        } catch (Exception e) {
            log.error("Error deleting old index data for symbol: {}", symbol, e);
        }
    }
}
