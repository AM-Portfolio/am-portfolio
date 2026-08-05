package com.portfolio.service.portfolio;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class PortfolioSummaryMongoService {

    private final MongoTemplate mongoTemplate;

    @Value("${portfolio.snapshot.freshness-minutes:15}")
    private int freshnessMinutes;

    @Async("taskExecutor")
    public void cachePortfolioSummary(PortfolioSummaryV1 summary, String userId, TimeInterval interval, String portfolioId) {
        String key = buildKey(userId, interval, portfolioId);
        try {
            Query query = new Query(Criteria.where("_id").is(key));
            Update update = new Update()
                .set("summary", summary)
                .set("updatedAt", LocalDateTime.now());
            
            mongoTemplate.upsert(query, update, "portfolio_summary_cache");
            log.debug("Cached calculated portfolio summary in MongoDB for key: {}", key);
        } catch (Exception e) {
            log.error("Error caching portfolio summary to Mongo for key {}: {}", key, e.getMessage());
        }
    }

    public Optional<PortfolioSummaryV1> getLatestFreshSummary(String userId, TimeInterval interval, String portfolioId) {
        String key = buildKey(userId, interval, portfolioId);
        try {
            Query query = new Query(Criteria.where("_id").is(key));
            MongoCacheDoc doc = mongoTemplate.findOne(query, MongoCacheDoc.class, "portfolio_summary_cache");
            
            if (doc != null && doc.getSummary() != null) {
                LocalDateTime cutoff = LocalDateTime.now().minusMinutes(freshnessMinutes);
                if (doc.getUpdatedAt() != null && doc.getUpdatedAt().isAfter(cutoff)) {
                    log.debug("Found fresh portfolio summary in Mongo cache for key: {}", key);
                    return Optional.of(doc.getSummary());
                } else {
                    log.debug("Found stale portfolio summary in Mongo cache for key: {}", key);
                }
            }
        } catch (Exception e) {
            log.error("Error retrieving portfolio summary from Mongo cache for key {}: {}", key, e.getMessage());
        }
        return Optional.empty();
    }

    public void deleteCache(String userId, TimeInterval interval, String portfolioId) {
        String key = buildKey(userId, interval, portfolioId);
        try {
            Query query = new Query(Criteria.where("_id").is(key));
            mongoTemplate.remove(query, "portfolio_summary_cache");
            log.debug("Deleted stale portfolio summary from Mongo for key: {}", key);
        } catch (Exception e) {
            log.error("Error deleting portfolio summary from Mongo cache for key {}: {}", key, e.getMessage());
        }
    }

    private String buildKey(String userId, TimeInterval interval, String portfolioId) {
        String intervalCode = interval != null ? interval.getCode() : "default";
        String portPart = (portfolioId != null && !portfolioId.trim().isEmpty()) ? portfolioId : "all";
        return userId + ":" + portPart + ":" + intervalCode;
    }

    @lombok.Data
    private static class MongoCacheDoc {
        private String id;
        private PortfolioSummaryV1 summary;
        private LocalDateTime updatedAt;
    }
}
