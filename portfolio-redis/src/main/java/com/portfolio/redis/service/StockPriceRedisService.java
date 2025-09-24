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

import com.am.common.investment.model.equity.EquityPrice;
import com.portfolio.model.cache.StockPriceCache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockPriceRedisService {

    private final RedisTemplate<String, StockPriceCache> stockPriceRedisTemplate;
    private static final int BATCH_SIZE = 100;

    @Value("${spring.data.redis.stock.ttl}")
    private Integer stockTtl;

    @Value("${spring.data.redis.stock.key-prefix}")
    private String stockKeyPrefix;

    @Value("${spring.data.redis.stock.historical.key-prefix}")
    private String historicalKeyPrefix;

    @Value("${spring.data.redis.stock.historical.ttl}")
    private Integer historicalTtl;

    @Async
    public CompletableFuture<Void> cacheEquityPriceUpdateBatch(List<EquityPrice> priceUpdates) {
        return CompletableFuture.runAsync(() -> {
            if (priceUpdates == null || priceUpdates.isEmpty()) {
                log.warn("Received empty or null price updates batch");
                return;
            }
            
            log.info("Starting to process {} price updates", priceUpdates.size());
            try {
                // Process in chunks of BATCH_SIZE
                for (int i = 0; i < priceUpdates.size(); i += BATCH_SIZE) {
                    int end = Math.min(i + BATCH_SIZE, priceUpdates.size());
                    List<EquityPrice> batch = priceUpdates.subList(i, end);
                    log.debug("Processing batch {} to {} of {}", i, end, priceUpdates.size());
                    processBatch(batch);
                }
                log.info("Successfully processed all {} price updates", priceUpdates.size());
            } catch (Exception e) {
                log.error("Error processing price update batch: {}", e.getMessage(), e);
                // Not rethrowing exception as per requirement to not acknowledge failures
            }
        });
    }

    private void processBatch(List<EquityPrice> batch) {
        try {
            long startTime = System.currentTimeMillis();
            
            Map<String, StockPriceCache> realtimeUpdates = batch.stream()
                .map(this::convertToStockPriceCache)
                .peek(price -> log.info("Creating realtime key for symbol: {}, price: {}", 
                    price.getSymbol(), price.getClosePrice()))
                .collect(Collectors.toMap(
                    price -> {
                        String key = stockKeyPrefix + price.getSymbol();
                        log.info("Generated realtime key: {}", key);
                        return key;
                    },
                    price -> price
                ));

            Map<String, StockPriceCache> historicalUpdates = batch.stream()
                .map(this::convertToStockPriceCache)
                .peek(price -> log.info("Creating historical key for symbol: {}, timestamp: {}, price: {}", 
                    price.getSymbol(), price.getTimestamp(), price.getClosePrice()))
                .collect(Collectors.toMap(
                    price -> {
                        String key = historicalKeyPrefix + price.getSymbol() + ":" + price.getTimestamp().toString();
                        log.info("Generated historical key: {}", key);
                        return key;
                    },
                    price -> price
                ));

            try {
                log.info("Writing {} realtime updates to Redis", realtimeUpdates.size());
                stockPriceRedisTemplate.opsForValue().multiSet(realtimeUpdates);
                realtimeUpdates.keySet().forEach(key -> {
                    boolean success = stockPriceRedisTemplate.expire(key, Duration.ofSeconds(stockTtl)).booleanValue();
                    log.info("Set TTL for realtime key: {}, success: {}", key, success);
                });
                log.debug("Successfully cached realtime price updates");
            } catch (Exception e) {
                log.error("Failed to cache realtime updates: {}", e.getMessage(), e);
            }

            try {
                log.info("Writing {} historical updates to Redis", historicalUpdates.size());
                stockPriceRedisTemplate.opsForValue().multiSet(historicalUpdates);
                historicalUpdates.keySet().forEach(key -> {
                    boolean success = stockPriceRedisTemplate.expire(key, Duration.ofSeconds(historicalTtl)).booleanValue();
                    log.info("Set TTL for historical key: {}, success: {}", key, success);
                });
                log.debug("Successfully cached historical price updates");
            } catch (Exception e) {
                log.error("Failed to cache historical updates: {}", e.getMessage(), e);
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Batch processing completed in {}ms for {} updates", duration, batch.size());
        } catch (Exception e) {
            log.error("Failed to process batch: {}", e.getMessage(), e);
        }
    }

    private StockPriceCache convertToStockPriceCache(EquityPrice price) {
        return StockPriceCache.builder()
            .symbol(price.getSymbol())
            .closePrice(price.getLastPrice())
            .timestamp(price.getTime())
            .build();
    }

    public Optional<StockPriceCache> getLatestPrice(String symbol) {
        try {
            String key = stockKeyPrefix + symbol;
            StockPriceCache price = stockPriceRedisTemplate.opsForValue().get(key);
            return Optional.ofNullable(price);
        } catch (Exception e) {
            log.error("Error retrieving price for symbol: {}", symbol, e);
            return Optional.empty();
        }
    }

    public List<StockPriceCache> getHistoricalPrices(String symbol, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            String pattern = historicalKeyPrefix + symbol + ":*";
            List<StockPriceCache> historicalPrices = new ArrayList<>();
            
            log.info("Searching with pattern: {} for time range: {} to {}", pattern, startTime, endTime);
            var keys = stockPriceRedisTemplate.keys(pattern);
            log.info("Found {} keys matching pattern {}", keys.size(), pattern);
            
            // Sort keys to get most recent first
            List<String> sortedKeys = new ArrayList<>(keys);
            sortedKeys.sort((k1, k2) -> k2.compareTo(k1));  // Reverse order to get newest first
            
            for (String key : sortedKeys) {
                StockPriceCache price = stockPriceRedisTemplate.opsForValue().get(key);
                log.info("Key: {}, Price: {}", key, price);
                if (price != null && price.getTimestamp() != null) {
                    // Add the most recent price we find, regardless of time range
                    // This ensures we always get at least one price if available
                    if (historicalPrices.isEmpty()) {
                        historicalPrices.add(price);
                        log.info("Added most recent price for key: {}, timestamp: {}", key, price.getTimestamp());
                        break;  // We only need the most recent price
                    }
                } else {
                    log.warn("Null price or timestamp for key: {}", key);
                }
            }

            log.info("Returning {} historical prices for symbol: {}", historicalPrices.size(), symbol);
            return historicalPrices;
        } catch (Exception e) {
            log.error("Error retrieving historical prices for symbol: {}", symbol, e);
            return List.of();
        }
    }

    public void deleteOldPrices(String symbol, LocalDateTime beforeTime) {
        try {
            String pattern = historicalKeyPrefix + symbol + ":*";
            stockPriceRedisTemplate.keys(pattern).stream()
                .map(key -> Map.entry(key, stockPriceRedisTemplate.opsForValue().get(key)))
                .filter(entry -> entry.getValue() != null && 
                    entry.getValue().getTimestamp().isBefore(beforeTime.toInstant(ZoneOffset.UTC)))
                .forEach(entry -> stockPriceRedisTemplate.delete(entry.getKey()));
        } catch (Exception e) {
            log.error("Error deleting old prices for symbol: {}", symbol, e);
        }
    }
}
