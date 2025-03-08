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

import com.am.common.investment.model.equity.EquityPrice;
import com.portfolio.model.StockPriceCache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockPriceRedisService {

    private final RedisTemplate<String, StockPriceCache> stockPriceRedisTemplate;
    private static final String PRICE_KEY_PREFIX = "stock:price:";
    private static final String HISTORICAL_KEY_PREFIX = "stock:historical:";
    private static final Duration REALTIME_TTL = Duration.ofHours(24); // Keep realtime data for 24 hours
    private static final Duration HISTORICAL_TTL = Duration.ofDays(30); // Keep historical data for 30 days
    private static final int BATCH_SIZE = 100;

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
                .collect(Collectors.toMap(
                    price -> PRICE_KEY_PREFIX + price.getSymbol(),
                    price -> price
                ));

            Map<String, StockPriceCache> historicalUpdates = batch.stream()
                .map(this::convertToStockPriceCache)
                .collect(Collectors.toMap(
                    price -> HISTORICAL_KEY_PREFIX + price.getSymbol() + ":" + price.getTimestamp(),
                    price -> price
                ));

            try {
                // Batch write realtime updates
                stockPriceRedisTemplate.opsForValue().multiSet(realtimeUpdates);
                realtimeUpdates.keySet().forEach(key -> 
                    stockPriceRedisTemplate.expire(key, REALTIME_TTL));
                log.debug("Successfully cached {} realtime price updates", realtimeUpdates.size());
            } catch (Exception e) {
                log.error("Failed to cache realtime updates: {}", e.getMessage(), e);
            }

            try {
                // Batch write historical updates
                stockPriceRedisTemplate.opsForValue().multiSet(historicalUpdates);
                historicalUpdates.keySet().forEach(key -> 
                    stockPriceRedisTemplate.expire(key, HISTORICAL_TTL));
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

    private StockPriceCache convertToStockPriceCache(EquityPrice price) {
        return StockPriceCache.builder()
            .symbol(price.getSymbol())
            .closePrice(price.getClose())
            .timestamp(price.getTime())
            .build();
    }

    public Optional<StockPriceCache> getLatestPrice(String symbol) {
        try {
            String key = PRICE_KEY_PREFIX + symbol;
            StockPriceCache price = stockPriceRedisTemplate.opsForValue().get(key);
            return Optional.ofNullable(price);
        } catch (Exception e) {
            log.error("Error retrieving price for symbol: {}", symbol, e);
            return Optional.empty();
        }
    }

    public List<StockPriceCache> getHistoricalPrices(String symbol, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            String pattern = HISTORICAL_KEY_PREFIX + symbol + ":*";
            List<StockPriceCache> historicalPrices = new ArrayList<>();
            
            // Get all keys matching the pattern
            stockPriceRedisTemplate.keys(pattern).stream()
                .map(key -> stockPriceRedisTemplate.opsForValue().get(key))
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
