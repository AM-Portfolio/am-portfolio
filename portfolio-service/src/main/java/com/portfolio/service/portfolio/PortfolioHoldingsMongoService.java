package com.portfolio.service.portfolio;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.PortfolioHoldings;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class PortfolioHoldingsMongoService {

    private final MongoTemplate mongoTemplate;

    @Value("${portfolio.snapshot.freshness-minutes:15}")
    private int freshnessMinutes;

    @Async("taskExecutor")
    public void cachePortfolioHoldings(PortfolioHoldings holdings, String userId, TimeInterval interval, String portfolioId) {
        String key = buildKey(userId, interval, portfolioId);
        try {
            Query query = new Query(Criteria.where("_id").is(key));
            Update update = new Update()
                .set("holdings", holdings)
                .set("updatedAt", LocalDateTime.now());
            
            mongoTemplate.upsert(query, update, "portfolio_holdings_cache");
            log.debug("Cached calculated portfolio in MongoDB for key: {}", key);
        } catch (Exception e) {
            log.error("Error caching portfolio holdings to Mongo for key {}: {}", key, e.getMessage());
        }
    }

    public Optional<PortfolioHoldings> getLatestFreshHoldings(String userId, TimeInterval interval, String portfolioId) {
        String key = buildKey(userId, interval, portfolioId);
        try {
            Query query = new Query(Criteria.where("_id").is(key));
            MongoCacheDoc doc = mongoTemplate.findOne(query, MongoCacheDoc.class, "portfolio_holdings_cache");
            
            if (doc != null && doc.getHoldings() != null) {
                LocalDateTime cutoff = LocalDateTime.now().minusMinutes(freshnessMinutes);
                if (doc.getUpdatedAt() != null && doc.getUpdatedAt().isAfter(cutoff)) {
                    log.debug("Found fresh portfolio holdings in Mongo cache for key: {}", key);
                    return Optional.of(doc.getHoldings());
                } else {
                    log.debug("Found stale portfolio holdings in Mongo cache for key: {}", key);
                }
            }
        } catch (Exception e) {
            log.error("Error retrieving portfolio holdings from Mongo cache for key {}: {}", key, e.getMessage());
        }
        return Optional.empty();
    }

    private String buildKey(String userId, TimeInterval interval, String portfolioId) {
        String intervalCode = interval != null ? interval.getCode() : "default";
        String portPart = (portfolioId != null && !portfolioId.trim().isEmpty()) ? portfolioId : "all";
        return userId + ":" + portPart + ":" + intervalCode;
    }

    @lombok.Data
    private static class MongoCacheDoc {
        private String id;
        private PortfolioHoldings holdings;
        private LocalDateTime updatedAt;
    }
}
