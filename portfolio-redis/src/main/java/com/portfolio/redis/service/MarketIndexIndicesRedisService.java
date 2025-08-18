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

import com.am.common.investment.model.equity.MarketIndexIndices;
import com.portfolio.model.market.IndexIndices;
import com.portfolio.model.TimeInterval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketIndexIndicesRedisService {

    private final RedisTemplate<String, IndexIndices> marketIndexIndicesRedisTemplate;

    @Value("${spring.data.redis.market-indices.ttl}")
    private Integer marketIndicesTtl;

    @Value("${spring.data.redis.market-indices.key-prefix}")
    private String marketIndicesKeyPrefix;

    @Value("${spring.data.redis.market-indices.historical.ttl}")
    private Integer marketIndicesHistoricalTtl;

    @Value("${spring.data.redis.market-indices.historical.key-prefix}")
    private String marketIndicesHistoricalKeyPrefix;

    private static final int BATCH_SIZE = 100;

    @Async
    public CompletableFuture<Void> cacheMarketIndexIndicesUpdateBatch(List<MarketIndexIndices> marketIndexIndicesUpdates) {
        return CompletableFuture.runAsync(() -> {
            if (marketIndexIndicesUpdates == null || marketIndexIndicesUpdates.isEmpty()) {
                log.warn("Received empty or null market index indices updates batch");
                return;
            }
            
            log.info("Starting to process {} market index indices updates", marketIndexIndicesUpdates.size());
            try {
                // Process in chunks of BATCH_SIZE
                for (int i = 0; i < marketIndexIndicesUpdates.size(); i += BATCH_SIZE) {
                    int end = Math.min(i + BATCH_SIZE, marketIndexIndicesUpdates.size());
                    List<MarketIndexIndices> batch = marketIndexIndicesUpdates.subList(i, end);
                    log.debug("Processing batch {} to {} of {}", i, end, marketIndexIndicesUpdates.size());
                    processBatch(batch);
                }
                log.info("Successfully processed all {} market index indices updates", marketIndexIndicesUpdates.size());
            } catch (Exception e) {
                log.error("Error processing price update batch: {}", e.getMessage(), e);
                // Not rethrowing exception as per requirement to not acknowledge failures
            }
        });
    }

    private void processBatch(List<MarketIndexIndices> batch) {
        try {
            long startTime = System.currentTimeMillis();
            
            Map<String, IndexIndices> realtimeUpdates = batch.stream()
                .map(this::convertToIndexIndicesCache)
                .collect(Collectors.toMap(
                    price -> marketIndicesKeyPrefix + price.getIndexSymbol(),
                    price -> price
                ));

            Map<String, IndexIndices> historicalUpdates = batch.stream()
                .map(this::convertToIndexIndicesCache)
                .collect(Collectors.toMap(
                    price -> marketIndicesHistoricalKeyPrefix + price.getIndexSymbol() + ":" + price.getTimestamp(),
                    price -> price
                ));

            try {
                // Batch write realtime updates
                marketIndexIndicesRedisTemplate.opsForValue().multiSet(realtimeUpdates);
                realtimeUpdates.keySet().forEach(key -> 
                marketIndexIndicesRedisTemplate.expire(key, Duration.ofSeconds(marketIndicesTtl)));
                log.debug("Successfully cached {} realtime price updates", realtimeUpdates.size());
            } catch (Exception e) {
                log.error("Failed to cache realtime updates: {}", e.getMessage(), e);
            }

            try {
                // Batch write historical updates
                marketIndexIndicesRedisTemplate.opsForValue().multiSet(historicalUpdates);
                historicalUpdates.keySet().forEach(key -> 
                marketIndexIndicesRedisTemplate.expire(key, Duration.ofSeconds(marketIndicesHistoricalTtl)));
                log.debug("Successfully cached {} historical price updates", historicalUpdates.size());
            } catch (Exception e) {
                log.error("Failed to cache historical updates: {}", e.getMessage(), e);
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Batch processing completed in {}ms for {} updates", duration, batch.size());
        } catch (Exception e) {
            log.error("Failed to process batch: {}", e.getMessage(), e);
        }
    }

    private IndexIndices convertToIndexIndicesCache(MarketIndexIndices price) {
        return IndexIndices.builder()
            .key(price.getIndexSymbol())
            .indexSymbol(price.getIndexSymbol())
            .index(price.getIndex())
            .indexIndices(price)
            .timestamp(price.getTimestamp().toInstant(ZoneOffset.UTC))
            .build();
    }

    public Optional<IndexIndices> getPrice(String indexSymbol, TimeInterval timeInterval) {
        try {
            log.info("Searching price for indexSymbol: {} with timeInterval: {}", indexSymbol, timeInterval);
            
            // First try to get from real-time cache
            String key = marketIndicesKeyPrefix + indexSymbol;
            log.debug("Checking real-time cache for key: {}", key);
            IndexIndices price = marketIndexIndicesRedisTemplate.opsForValue().get(key);
            
            if (price != null) {
                log.info("Found price in real-time cache for indexSymbol: {}", indexSymbol);
                return Optional.of(price);
            }
            log.debug("No price found in real-time cache for indexSymbol: {}", indexSymbol);

            // If not found in real-time cache, search in given time interval if duration is not null
            if (timeInterval.getDuration() != null) {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime startTime = now.minus(timeInterval.getDuration());
                log.debug("Searching historical data for indexSymbol: {} in interval: {} to {}", 
                    indexSymbol, startTime, now);

                // Get historical prices for the given time interval
                List<IndexIndices> intervalPrices = getHistoricalPrices(indexSymbol, startTime, now);
                
                if (!intervalPrices.isEmpty()) {
                    log.info("Found price in historical data (interval) for indexSymbol: {}", indexSymbol);
                    return Optional.of(intervalPrices.get(0));
                }
                log.debug("No price found in historical data (interval) for indexSymbol: {}", indexSymbol);
            }

            // If not found in given interval, search in last 5 days
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime fiveDaysAgo = now.minusDays(5);
            log.debug("Searching historical data for indexSymbol: {} in last 5 days", indexSymbol);
            List<IndexIndices> historicalPrices = getHistoricalPrices(indexSymbol, fiveDaysAgo, now);
            
            if (!historicalPrices.isEmpty()) {
                log.info("Found price in historical data (5 days) for indexSymbol: {}", indexSymbol);
                return Optional.of(historicalPrices.get(0));
            }

            log.warn("No price found for indexSymbol: {} in real-time, given interval, or last 5 days", indexSymbol);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error retrieving price for indexSymbol: {}", indexSymbol, e);
            return Optional.empty();
        }
    }
    public Optional<IndexIndices> getLatestPrice(String key) {
        try {
            IndexIndices price = marketIndexIndicesRedisTemplate.opsForValue().get(key);
            return Optional.ofNullable(price);
        } catch (Exception e) {
            log.error("Error retrieving price for key: {}", key, e);
            return Optional.empty();
        }
    }

    public List<IndexIndices> getHistoricalPrices(String symbol, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            String pattern = marketIndicesHistoricalKeyPrefix + symbol + ":*";
            List<IndexIndices> historicalPrices = new ArrayList<>();
            
            // Get all keys matching the pattern
            marketIndexIndicesRedisTemplate.keys(pattern).stream()
                .map(key -> marketIndexIndicesRedisTemplate.opsForValue().get(key))
                .filter(price -> price != null &&
                    price.getTimestamp().isAfter(startTime.toInstant(ZoneOffset.UTC)) &&
                    price.getTimestamp().isBefore(endTime.toInstant(ZoneOffset.UTC)))
                .forEach(historicalPrices::add);

            return historicalPrices;
        } catch (Exception e) {
            log.error("Error retrieving historical prices for symbol: {}", symbol, e);
            return List.of();
        }
    }

    public void deleteOldPrices(String symbol, LocalDateTime beforeTime) {
        try {
            String pattern = marketIndicesHistoricalKeyPrefix + symbol + ":*";
            marketIndexIndicesRedisTemplate.keys(pattern).stream()
                .map(key -> Map.entry(key, marketIndexIndicesRedisTemplate.opsForValue().get(key)))
                .filter(entry -> entry.getValue() != null && 
                    entry.getValue().getTimestamp().isBefore(beforeTime.toInstant(ZoneOffset.UTC)))
                .forEach(entry -> marketIndexIndicesRedisTemplate.delete(entry.getKey()));
        } catch (Exception e) {
            log.error("Error deleting old prices for symbol: {}", symbol, e);
        }
    }
}
