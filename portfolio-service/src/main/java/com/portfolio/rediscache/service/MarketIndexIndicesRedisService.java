package com.portfolio.rediscache.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.am.common.investment.model.equity.MarketIndexIndices;
import com.portfolio.model.MarketIndexIndicesCache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketIndexIndicesRedisService {

    private final RedisTemplate<String, MarketIndexIndicesCache> marketIndexIndicesRedisTemplate;
    private static final String PRICE_KEY_PREFIX = "marketindex:value:";
    private static final String HISTORICAL_KEY_PREFIX = "marketindex:historical:";
    private static final Duration REALTIME_TTL = Duration.ofHours(24); // Keep realtime data for 24 hours
    private static final Duration HISTORICAL_TTL = Duration.ofDays(30); // Keep historical data for 30 days
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
            
            Map<String, MarketIndexIndicesCache> realtimeUpdates = batch.stream()
                .map(this::convertToMarketIndexIndicesCache)
                .collect(Collectors.toMap(
                    price -> PRICE_KEY_PREFIX + price.getKey(),
                    price -> price
                ));

            Map<String, MarketIndexIndicesCache> historicalUpdates = batch.stream()
                .map(this::convertToMarketIndexIndicesCache)
                .collect(Collectors.toMap(
                    price -> HISTORICAL_KEY_PREFIX + price.getKey() + ":" + price.getTimestamp(),
                    price -> price
                ));

            try {
                // Batch write realtime updates
                marketIndexIndicesRedisTemplate.opsForValue().multiSet(realtimeUpdates);
                realtimeUpdates.keySet().forEach(key -> 
                marketIndexIndicesRedisTemplate.expire(key, REALTIME_TTL));
                log.debug("Successfully cached {} realtime price updates", realtimeUpdates.size());
            } catch (Exception e) {
                log.error("Failed to cache realtime updates: {}", e.getMessage(), e);
            }

            try {
                // Batch write historical updates
                marketIndexIndicesRedisTemplate.opsForValue().multiSet(historicalUpdates);
                historicalUpdates.keySet().forEach(key -> 
                marketIndexIndicesRedisTemplate.expire(key, HISTORICAL_TTL));
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

    private MarketIndexIndicesCache convertToMarketIndexIndicesCache(MarketIndexIndices price) {
        return MarketIndexIndicesCache.builder()
            .key(price.getKey())
            .index(price.getIndex())
            .indexSymbol(price.getIndexSymbol())
            .indexIndices(price)
            .timestamp(price.getTimestamp().toInstant(ZoneOffset.UTC))
            .build();
    }

    public Optional<MarketIndexIndicesCache> getLatestPrice(String key) {
        try {
            MarketIndexIndicesCache price = marketIndexIndicesRedisTemplate.opsForValue().get(key);
            return Optional.ofNullable(price);
        } catch (Exception e) {
            log.error("Error retrieving price for key: {}", key, e);
            return Optional.empty();
        }
    }

    public List<MarketIndexIndicesCache> getHistoricalPrices(String symbol, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            String pattern = HISTORICAL_KEY_PREFIX + symbol + ":*";
            List<MarketIndexIndicesCache> historicalPrices = new ArrayList<>();
            
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
            String pattern = HISTORICAL_KEY_PREFIX + symbol + ":*";
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
