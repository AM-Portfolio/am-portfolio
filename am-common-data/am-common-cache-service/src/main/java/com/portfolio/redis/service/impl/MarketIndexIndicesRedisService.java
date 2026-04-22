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

// import com.am.common.investment.model.equity.MarketIndexIndices;
// import com.portfolio.redis.model.MarketIndexIndicesCache;
// import com.portfolio.redis.service.base.AbstractRedisService;
// import com.portfolio.redis.util.RedisUtils;

// import lombok.extern.slf4j.Slf4j;

// @Slf4j
// @Service
// public class MarketIndexIndicesRedisService extends AbstractRedisService<String, MarketIndexIndicesCache> {

//     private static final int BATCH_SIZE = 100;

//     @Value("${spring.data.redis.market-indices.ttl}")
//     private Integer marketIndicesTtl;

//     @Value("${spring.data.redis.market-indices.key-prefix}")
//     private String marketIndicesKeyPrefix;

//     @Value("${spring.data.redis.market-indices.historical.ttl}")
//     private Integer marketIndicesHistoricalTtl;

//     @Value("${spring.data.redis.market-indices.historical.key-prefix}")
//     private String marketIndicesHistoricalKeyPrefix;

//     public MarketIndexIndicesRedisService(
//             RedisTemplate<String, MarketIndexIndicesCache> redisTemplate) {
//         super(redisTemplate);
//     }

//     @Override
//     protected String getServiceName() {
//         return "MarketIndices";
//     }

//     @Override
//     protected Duration getDefaultTtl() {
//         return Duration.ofSeconds(marketIndicesTtl);
//     }

//     @Override
//     protected String buildKey(Object... parts) {
//         return RedisUtils.buildKey(marketIndicesKeyPrefix, parts);
//     }

//     @Async
//     public CompletableFuture<Void> cacheMarketIndexUpdateBatch(List<MarketIndexIndices> indexUpdates) {
//         return CompletableFuture.runAsync(() -> {
//             if (indexUpdates == null || indexUpdates.isEmpty()) {
//                 log.warn("Received empty or null market index updates batch");
//                 return;
//             }
            
//             log.info("Starting to process {} market index updates", indexUpdates.size());
            
//             // Process updates in batches
//             for (int i = 0; i < indexUpdates.size(); i += BATCH_SIZE) {
//                 int end = Math.min(i + BATCH_SIZE, indexUpdates.size());
//                 List<MarketIndexIndices> batch = indexUpdates.subList(i, end);
//                 processBatch(batch);
//             }
//         });
//     }

//     private void processBatch(List<MarketIndexIndices> batch) {
//         try {
//             // Prepare realtime updates
//             Map<String, MarketIndexIndicesCache> realtimeUpdates = batch.stream()
//                 .map(this::convertToMarketIndexIndicesCache)
//                 .collect(Collectors.toMap(
//                     index -> buildKey(index.getIndexSymbol()),
//                     index -> index
//                 ));

//             // Prepare historical updates
//             Map<String, MarketIndexIndicesCache> historicalUpdates = batch.stream()
//                 .map(this::convertToMarketIndexIndicesCache)
//                 .collect(Collectors.toMap(
//                     index -> buildHistoricalKey(index.getIndexSymbol(), LocalDateTime.now().toInstant(ZoneOffset.UTC)),
//                     index -> index
//                 ));

//             // Cache realtime updates
//             setBatch(realtimeUpdates, getDefaultTtl());

//             // Cache historical updates
//             setBatch(historicalUpdates, Duration.ofSeconds(marketIndicesHistoricalTtl));
//         } catch (Exception e) {
//             log.error("Error processing market index update batch: {}", e.getMessage(), e);
//         }
//     }

//     public List<MarketIndexIndicesCache> getHistoricalPrices(String symbol, LocalDateTime startTime, LocalDateTime endTime) {
//         try {
//             String pattern = marketIndicesHistoricalKeyPrefix + symbol + ":*";
//             List<MarketIndexIndicesCache> historicalPrices = new ArrayList<>();
            
//             redisTemplate.keys(pattern).stream()
//                 .map(key -> Optional.ofNullable(get(key).orElse(null)))
//                 .filter(Optional::isPresent)
//                 .map(Optional::get)
//                 .filter(index -> isWithinTimeRange(LocalDateTime.now().toInstant(ZoneOffset.UTC), startTime, endTime))
//                 .forEach(historicalPrices::add);
            
//             return historicalPrices;
//         } catch (Exception e) {
//             log.error("Error retrieving historical prices for symbol {}: {}", symbol, e.getMessage(), e);
//             return new ArrayList<>();
//         }
//     }

//     public void deleteOldPrices(String symbol, LocalDateTime beforeTime) {
//         try {
//             String pattern = marketIndicesHistoricalKeyPrefix + symbol + ":*";
//             redisTemplate.keys(pattern).stream()
//                 .map(key -> Map.entry(key, get(key)))
//                 .filter(entry -> entry.getValue().isPresent())
//                 .filter(entry -> LocalDateTime.now().isBefore(beforeTime))
//                 .forEach(entry -> delete(entry.getKey()));
//         } catch (Exception e) {
//             log.error("Error deleting old market index data for symbol {}: {}", symbol, e.getMessage(), e);
//         }
//     }

//     private MarketIndexIndicesCache convertToMarketIndexIndicesCache(MarketIndexIndices index) {
//         return MarketIndexIndicesCache.builder()
//             .key(index.getIndexSymbol())
//             .indexSymbol(index.getIndexSymbol())
//             .index(index.getIndex())
//             .indexIndices(index)
//             .timestamp(index.getTimestamp())
//             .build();
//     }

//     private String buildHistoricalKey(String symbol, Instant timestamp) {
//         return RedisUtils.buildKey(marketIndicesHistoricalKeyPrefix, symbol, String.valueOf(timestamp.toEpochMilli()));
//     }

//     private boolean isWithinTimeRange(Instant timestamp, LocalDateTime startTime, LocalDateTime endTime) {
//         Instant startInstant = startTime.toInstant(ZoneOffset.UTC);
//         Instant endInstant = endTime.toInstant(ZoneOffset.UTC);
//         return !timestamp.isBefore(startInstant) && !timestamp.isAfter(endInstant);
//     }
// }
