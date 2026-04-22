// package com.portfolio.redis.service.impl;

// import java.time.Duration;
// import java.time.Instant;
// import java.time.LocalDateTime;
// import java.time.ZoneOffset;
// import java.util.ArrayList;
// import java.util.List;
// import java.util.Map;
// import java.util.Optional;
// import java.util.concurrent.CompletableFuture;
// import java.util.stream.Collectors;

// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.data.redis.core.RedisTemplate;
// import org.springframework.scheduling.annotation.Async;
// import org.springframework.stereotype.Service;

// import com.am.common.investment.model.equity.EquityPrice;
// import com.portfolio.redis.model.StockPriceCache;
// import com.portfolio.redis.service.base.AbstractRedisService;
// import com.portfolio.redis.util.RedisUtils;

// import lombok.extern.slf4j.Slf4j;

// @Slf4j
// @Service
// public class StockPriceRedisService extends AbstractRedisService<String, StockPriceCache> {

//     private static final int BATCH_SIZE = 100;

//     @Value("${spring.data.redis.stock.ttl}")
//     private Integer stockTtl;

//     @Value("${spring.data.redis.stock.key-prefix}")
//     private String stockKeyPrefix;

//     @Value("${spring.data.redis.stock.historical.key-prefix}")
//     private String historicalKeyPrefix;

//     @Value("${spring.data.redis.stock.historical.ttl}")
//     private Integer historicalTtl;

//     public StockPriceRedisService(
//             RedisTemplate<String, StockPriceCache> redisTemplate) {
//         super(redisTemplate);
//     }

//     @Override
//     protected String getServiceName() {
//         return "StockPrice";
//     }

//     @Override
//     protected Duration getDefaultTtl() {
//         return Duration.ofSeconds(stockTtl);
//     }

//     @Override
//     protected String buildKey(Object... parts) {
//         return RedisUtils.buildKey(stockKeyPrefix, parts);
//     }

//     @Async
//     public CompletableFuture<Void> cacheEquityPriceUpdateBatch(List<EquityPrice> priceUpdates) {
//         return CompletableFuture.runAsync(() -> {
//             if (priceUpdates == null || priceUpdates.isEmpty()) {
//                 log.warn("Received empty or null price updates batch");
//                 return;
//             }
            
//             log.info("Starting to process {} price updates", priceUpdates.size());
            
//             // Process updates in batches
//             for (int i = 0; i < priceUpdates.size(); i += BATCH_SIZE) {
//                 int end = Math.min(i + BATCH_SIZE, priceUpdates.size());
//                 List<EquityPrice> batch = priceUpdates.subList(i, end);
//                 processBatch(batch);
//             }
//         });
//     }

//     private void processBatch(List<EquityPrice> batch) {
//         try {
//             // Prepare realtime updates
//             Map<String, StockPriceCache> realtimeUpdates = batch.stream()
//                 .map(this::convertToStockPriceCache)
//                 .collect(Collectors.toMap(
//                     price -> buildKey(price.getSymbol()),
//                     price -> price
//                 ));

//             // Prepare historical updates
//             Map<String, StockPriceCache> historicalUpdates = batch.stream()
//                 .map(this::convertToStockPriceCache)
//                 .collect(Collectors.toMap(
//                     price -> buildHistoricalKey(price.getSymbol(), price.getTimestamp().toInstant(ZoneOffset.UTC)),
//                     price -> price
//                 ));

//             // Cache realtime updates
//             setBatch(realtimeUpdates, getDefaultTtl());

//             // Cache historical updates
//             setBatch(historicalUpdates, Duration.ofSeconds(historicalTtl));
//         } catch (Exception e) {
//             log.error("Error processing price update batch: {}", e.getMessage(), e);
//         }
//     }

//     public Optional<StockPriceCache> getLatestPrice(String symbol) {
//         return get(buildKey(symbol));
//     }

//     public List<StockPriceCache> getHistoricalPrices(String symbol, LocalDateTime startTime, LocalDateTime endTime) {
//         try {
//             String pattern = historicalKeyPrefix + symbol + ":*";
//             List<StockPriceCache> historicalPrices = new ArrayList<>();
            
//             redisTemplate.keys(pattern).stream()
//                 .map(key -> get(key))
//                 .filter(Optional::isPresent)
//                 .map(Optional::get)
//                 .filter(price -> isWithinTimeRange(price.getTimestamp(), startTime, endTime))
//                 .forEach(historicalPrices::add);
            
//             return historicalPrices;
//         } catch (Exception e) {
//             log.error("Error retrieving historical prices for symbol {}: {}", symbol, e.getMessage(), e);
//             return new ArrayList<>();
//         }
//     }

//     public void deleteOldPrices(String symbol, Instant beforeTime) {
//         try {
//             String pattern = historicalKeyPrefix + symbol + ":*";
//             redisTemplate.keys(pattern).stream()
//                 .map(key -> Map.entry(key, get(key)))
//                 .filter(entry -> entry.getValue().isPresent())
//                 .filter(entry -> entry.getValue().get().getTimestamp()
//                     .isBefore(beforeTime))
//                 .forEach(entry -> delete(entry.getKey()));
//         } catch (Exception e) {
//             log.error("Error deleting old prices for symbol {}: {}", symbol, e.getMessage(), e);
//         }
//     }

//     private StockPriceCache convertToStockPriceCache(EquityPrice price) {
//         return StockPriceCache.builder()
//             .symbol(price.getSymbol())
//             .isin(price.getIsin())
//             .price(price.getValue())
//             .change(price.getPriceChange())
//             .changePercent(price.getPriceChangePercent())
//             .volume(price.getVolume())
//             .equityPrice(price)
//             .timestamp(price.getDate().toInstant(ZoneOffset.UTC))
//             .build();
//     }

//     private String buildHistoricalKey(String symbol, Instant timestamp) {
//         return RedisUtils.buildKey(historicalKeyPrefix, symbol, String.valueOf(timestamp.toEpochMilli()));
//     }

//     private boolean isWithinTimeRange(Instant timestamp, LocalDateTime startTime, LocalDateTime endTime) {
//         return !timestamp.isBefore(startTime.toInstant(ZoneOffset.UTC)) && !timestamp.isAfter(endTime.toInstant(ZoneOffset.UTC));
//     }
// }
