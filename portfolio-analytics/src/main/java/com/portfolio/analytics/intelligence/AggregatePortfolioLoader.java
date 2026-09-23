package com.portfolio.analytics.intelligence;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads the authenticated owner's portfolios and merges BROKER books for All-Portfolios analytics.
 * Short L1 TTL avoids re-reading Mongo on every Overview /all/* call.
 */
@Service
@RequiredArgsConstructor
public class AggregatePortfolioLoader {

    static final long MERGE_TTL_MS = 45_000L;

    private final PortfolioService portfolioService;
    private final AggregatePortfolioFactory aggregatePortfolioFactory;

    private final ConcurrentHashMap<String, CachedMerge> mergeCache = new ConcurrentHashMap<>();

    public PortfolioModelV1 loadMerged(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId required");
        }
        String key = userId.trim();
        long now = System.currentTimeMillis();
        CachedMerge hit = mergeCache.get(key);
        if (hit != null && hit.expiresAtMs() > now) {
            return hit.model();
        }
        PortfolioModelV1 merged = aggregatePortfolioFactory.mergeBrokerBooks(
                key, portfolioService.getPortfoliosByUserId(key));
        mergeCache.put(key, new CachedMerge(merged, now + MERGE_TTL_MS));
        return merged;
    }

    /** Drop L1 merge after holdings writes so All-Portfolios reflects new books. */
    public void evict(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        mergeCache.remove(userId.trim());
    }

    private record CachedMerge(PortfolioModelV1 model, long expiresAtMs) {}
}
