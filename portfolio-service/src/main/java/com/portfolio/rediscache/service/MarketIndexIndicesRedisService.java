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
            
            log.info("Processing {} market index indices updates", marketIndexIndicesUpdates.size());
            try {
                // Process in chunks of BATCH_SIZE
                for (int i = 0; i < marketIndexIndicesUpdates.size(); i += BATCH_SIZE) {
                    int end = Math.min(i + BATCH_SIZE, marketIndexIndicesUpdates.size());
                    List<MarketIndexIndices> batch = marketIndexIndicesUpdates.subList(i, end);
                    log.debug("Processing batch {} to {} of {}", i, end, marketIndexIndicesUpdates.size());
                    processBatch(batch);
                }
                log.info("Market indices batch processing completed successfully");
            } catch (Exception e) {
                log.error("Failed to process market indices batch: {}", e.getMessage());
                log.debug("Error details: ", e);
            }
        });
    }

    private void processBatch(List<MarketIndexIndices> batch) {
        try {
            long startTime = System.currentTimeMillis();
            
            Map<String, MarketIndexIndicesCache> realtimeUpdates = batch.stream()
                .map(this::convertToMarketIndexIndicesCache)
                .collect(Collectors.toMap(
                    price -> generateRealtimeKey(price),
                    price -> price,
                    (existing, replacement) -> {
                        log.debug("Duplicate key detected for {}, keeping most recent value", existing.getIndexSymbol());
                        return existing.getTimestamp().isBefore(replacement.getTimestamp()) ? replacement : existing;
                    }
                ));

            Map<String, MarketIndexIndicesCache> historicalUpdates = batch.stream()
                .map(this::convertToMarketIndexIndicesCache)
                .collect(Collectors.toMap(
                    price -> generateHistoricalKey(price),
                    price -> price,
                    (existing, replacement) -> replacement
                ));

            try {
                // Batch write realtime updates
                marketIndexIndicesRedisTemplate.opsForValue().multiSet(realtimeUpdates);
                realtimeUpdates.keySet().forEach(key -> 
                marketIndexIndicesRedisTemplate.expire(key, REALTIME_TTL));
                log.info("Cached {} realtime market indices", realtimeUpdates.size());
                log.debug("Realtime updates: {}", realtimeUpdates.keySet());
            } catch (Exception e) {
                log.error("Failed to cache realtime market indices: {}", e.getMessage());
                log.debug("Failed realtime updates: {}", realtimeUpdates.keySet(), e);
            }

            try {
                // Batch write historical updates
                marketIndexIndicesRedisTemplate.opsForValue().multiSet(historicalUpdates);
                historicalUpdates.keySet().forEach(key -> 
                marketIndexIndicesRedisTemplate.expire(key, HISTORICAL_TTL));
                log.info("Cached {} historical market indices", historicalUpdates.size());
                log.debug("Historical updates: {}", historicalUpdates.keySet());
            } catch (Exception e) {
                log.error("Failed to cache historical market indices: {}", e.getMessage());
                log.debug("Failed historical updates: {}", historicalUpdates.keySet(), e);
            }

            long duration = System.currentTimeMillis() - startTime;
            log.debug("Batch processing completed in {}ms", duration);
        } catch (Exception e) {
            log.error("Failed to process market indices batch: {}", e.getMessage());
            log.debug("Error details: ", e);
        }
    }

    private String generateRealtimeKey(MarketIndexIndicesCache price) {
        return PRICE_KEY_PREFIX + price.getIndexSymbol();
    }

    private String generateHistoricalKey(MarketIndexIndicesCache price) {
        return HISTORICAL_KEY_PREFIX + price.getIndexSymbol() + ":" + price.getTimestamp().toEpochMilli();
    }

    private MarketIndexIndicesCache convertToMarketIndexIndicesCache(MarketIndexIndices price) {
        return MarketIndexIndicesCache.builder()
            .key(price.getIndexSymbol()) // Using indexSymbol as the key
            .index(price.getIndex())
            .indexSymbol(price.getIndexSymbol())
            .indexIndices(price)
            .timestamp(price.getTimestamp().toInstant(ZoneOffset.UTC))
            .build();
    }

    public Optional<MarketIndexIndicesCache> getLatestPrice(String symbol) {
        try {
            String key = PRICE_KEY_PREFIX + symbol;
            MarketIndexIndicesCache price = marketIndexIndicesRedisTemplate.opsForValue().get(key);
            if (price != null) {
                log.info("Retrieved latest price for {}", symbol);
                log.debug("Price details: {}", price);
            } else {
                log.info("No price found for {}", symbol);
            }
            return Optional.ofNullable(price);
        } catch (Exception e) {
            log.error("Failed to retrieve price for {}: {}", symbol, e.getMessage());
            log.debug("Error details: ", e);
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

            log.info("Retrieved {} historical prices for {}", historicalPrices.size(), symbol);
            log.debug("Historical prices: {}", historicalPrices);
            return historicalPrices;
        } catch (Exception e) {
            log.error("Failed to retrieve historical prices for {}: {}", symbol, e.getMessage());
            log.debug("Error details: ", e);
            return List.of();
        }
    }

    public void deleteOldPrices(String symbol, LocalDateTime beforeTime) {
        try {
            String pattern = HISTORICAL_KEY_PREFIX + symbol + ":*";
            List<String> deletedKeys = marketIndexIndicesRedisTemplate.keys(pattern).stream()
                .map(key -> Map.entry(key, marketIndexIndicesRedisTemplate.opsForValue().get(key)))
                .filter(entry -> entry.getValue() != null && 
                    entry.getValue().getTimestamp().isBefore(beforeTime.toInstant(ZoneOffset.UTC)))
                .map(entry -> {
                    marketIndexIndicesRedisTemplate.delete(entry.getKey());
                    return entry.getKey();
                })
                .collect(Collectors.toList());
            
            log.info("Deleted {} old prices for {}", deletedKeys.size(), symbol);
            log.debug("Deleted keys: {}", deletedKeys);
        } catch (Exception e) {
            log.error("Failed to delete old prices for {}: {}", symbol, e.getMessage());
            log.debug("Error details: ", e);
        }
    }
}
