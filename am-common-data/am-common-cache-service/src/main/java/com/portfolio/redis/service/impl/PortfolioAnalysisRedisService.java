package com.portfolio.redis.service.impl;

import java.time.Duration;
import java.time.Instant;
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

import com.portfolio.redis.model.TimeInterval;
import com.portfolio.redis.model.PortfolioAnalysis;
import com.portfolio.redis.service.base.AbstractRedisService;
import com.portfolio.redis.util.RedisUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PortfolioAnalysisRedisService extends AbstractRedisService<String, PortfolioAnalysis> {

    private static final int BATCH_SIZE = 100;

    @Value("${spring.data.redis.portfolio-analysis.ttl}")
    private Integer analysisTtl;

    @Value("${spring.data.redis.portfolio-analysis.key-prefix}")
    private String analysisKeyPrefix;

    @Value("${spring.data.redis.portfolio-analysis.historical.key-prefix}")
    private String historicalKeyPrefix;

    @Value("${spring.data.redis.portfolio-analysis.historical.ttl}")
    private Integer historicalTtl;

    public PortfolioAnalysisRedisService(
            RedisTemplate<String, PortfolioAnalysis> redisTemplate) {
        super(redisTemplate);
    }

    @Override
    protected String getServiceName() {
        return "PortfolioAnalysis";
    }

    @Override
    protected Duration getDefaultTtl() {
        return Duration.ofSeconds(analysisTtl);
    }

    @Override
    protected String buildKey(Object... parts) {
        return RedisUtils.buildKey(analysisKeyPrefix, parts);
    }

    @Async
    public CompletableFuture<Void> cachePortfolioAnalysis(PortfolioAnalysis analysis, String userId, TimeInterval interval) {
        log.info("Starting async caching of portfolio analysis - User: {}, Interval: {}", 
            userId, interval != null ? interval.getCode() : "null");
        
        return CompletableFuture.runAsync(() -> {
            String key = buildKey(userId, interval != null ? interval.getCode() : "all");
            Duration ttl = getEffectiveTtl(interval);
            
            set(key, analysis, ttl);
            log.info("Successfully cached portfolio analysis - User: {}, Key: {}, TTL: {} seconds", 
                userId, key, ttl.getSeconds());
        });
    }

    public Optional<PortfolioAnalysis> getLatestAnalysis(String userId, TimeInterval interval) {
        log.info("Retrieving latest portfolio analysis - User: {}, Interval: {}", 
            userId, interval != null ? interval.getCode() : "null");
            
        String key = buildKey(userId, interval != null ? interval.getCode() : "all");
        Optional<PortfolioAnalysis> analysis = get(key);
        
        if (analysis.isPresent() && interval != null && interval.getDuration() != null) {
            // Check if the analysis is still fresh (within the interval duration)
            Instant cutoff = Instant.now().minus(interval.getDuration());
            
            if (analysis.get().getLastUpdated().toInstant(ZoneOffset.UTC).isAfter(cutoff)) {
                log.info("Found fresh portfolio analysis in cache - User: {}, Key: {}, LastUpdated: {}", 
                    userId, key, analysis.get().getLastUpdated());
                return analysis;
            } else {
                log.info("Found stale portfolio analysis in cache - User: {}, Key: {}, LastUpdated: {}, deleting", 
                    userId, key, analysis.get().getLastUpdated());
                delete(key);
                return Optional.empty();
            }
        }
        
        return analysis;
    }

    @Async
    public CompletableFuture<Void> cacheBatchAnalysis(List<PortfolioAnalysis> analyses, String userId, TimeInterval interval) {
        return CompletableFuture.runAsync(() -> {
            if (analyses == null || analyses.isEmpty()) {
                log.warn("Received empty or null analysis batch");
                return;
            }
            
            log.info("Starting to process {} portfolio analyses", analyses.size());
            
            // Process updates in batches
            for (int i = 0; i < analyses.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, analyses.size());
                List<PortfolioAnalysis> batch = analyses.subList(i, end);
                processBatch(batch, userId, interval);
            }
        });
    }

    private void processBatch(List<PortfolioAnalysis> batch, String userId, TimeInterval interval) {
        try {
            // Prepare realtime updates
            Map<String, PortfolioAnalysis> realtimeUpdates = batch.stream()
                .collect(Collectors.toMap(
                    analysis -> buildKey(userId, interval != null ? interval.getCode() : "all"),
                    analysis -> analysis
                ));

            // Prepare historical updates
            Map<String, PortfolioAnalysis> historicalUpdates = batch.stream()
                .collect(Collectors.toMap(
                    analysis -> buildHistoricalKey(userId, interval, analysis.getLastUpdated().toInstant(ZoneOffset.UTC)),
                    analysis -> analysis
                ));

            // Cache realtime updates
            setBatch(realtimeUpdates, getDefaultTtl());

            // Cache historical updates
            setBatch(historicalUpdates, Duration.ofSeconds(historicalTtl));
        } catch (Exception e) {
            log.error("Error processing analysis batch: {}", e.getMessage(), e);
        }
    }

    public List<PortfolioAnalysis> getHistoricalAnalysis(String userId, TimeInterval interval, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            String pattern = buildHistoricalPattern(userId, interval);
            List<PortfolioAnalysis> historicalAnalysis = new ArrayList<>();
            
            redisTemplate.keys(pattern).stream()
                .map(key -> get(key))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(analysis -> isWithinTimeRange(analysis.getLastUpdated(), startTime, endTime))
                .forEach(historicalAnalysis::add);
            
            return historicalAnalysis;
        } catch (Exception e) {
            log.error("Error retrieving historical analysis for user {}: {}", userId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    public void deleteOldAnalysis(String userId, TimeInterval interval, LocalDateTime beforeTime) {
        try {
            String pattern = buildHistoricalPattern(userId, interval);
            redisTemplate.keys(pattern).stream()
                .map(key -> Map.entry(key, get(key)))
                .filter(entry -> entry.getValue().isPresent())
                .filter(entry -> entry.getValue().get().getLastUpdated()
                    .isBefore(beforeTime))
                .forEach(entry -> delete(entry.getKey()));
        } catch (Exception e) {
            log.error("Error deleting old analysis for user {}: {}", userId, e.getMessage(), e);
        }
    }

    private String buildHistoricalPattern(String userId, TimeInterval interval) {
        return RedisUtils.buildKey(historicalKeyPrefix, userId, interval != null ? interval.getCode() : "all", "*");
    }

    private String buildHistoricalKey(String userId, TimeInterval interval, java.time.Instant timestamp) {
        return RedisUtils.buildKey(historicalKeyPrefix, userId, interval != null ? interval.getCode() : "all", String.valueOf(timestamp.toEpochMilli()));
    }

    private boolean isWithinTimeRange(LocalDateTime dateTime, LocalDateTime startTime, LocalDateTime endTime) {
        return !dateTime.isBefore(startTime) && !dateTime.isAfter(endTime);
    }

    private Duration getEffectiveTtl(TimeInterval interval) {
        if (interval != null && interval.getDuration() != null) {
            Duration intervalDuration = interval.getDuration();
            Duration defaultDuration = getDefaultTtl();
            return intervalDuration.compareTo(defaultDuration) < 0 ? intervalDuration : defaultDuration;
        }
        return getDefaultTtl();
    }
}
