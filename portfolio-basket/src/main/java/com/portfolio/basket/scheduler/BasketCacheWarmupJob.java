package com.portfolio.basket.scheduler;

import com.portfolio.basket.service.BasketCatalogService;
import com.portfolio.basket.service.EnrichedEtfService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Warms L1/L2 ETF enriched cache for featured catalog tickers (batch + perf fill).
 * Runs off the Discover request path so opportunities stay ≤1s.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BasketCacheWarmupJob {

    private final BasketCatalogService catalogService;
    private final EnrichedEtfService enrichedEtfService;

    @PostConstruct
    @Async("taskExecutor")
    public void warmUpTopEtfs() {
        try {
            List<String> top = catalogService.getTopEtfSymbols();
            if (top == null || top.isEmpty()) {
                log.info("Cache warm-up: no featured ETF symbols");
                return;
            }
            log.info("Cache warm-up: batch loading {} ETF tickers", top.size());
            // discoverFastPath=false → allow enrich + hist into L1/Redis off-path
            enrichedEtfService.getEnrichedEtfsBatch(top, false);
            log.info("Cache warm-up: done for {} ETFs", top.size());
        } catch (Exception e) {
            log.warn("BasketCacheWarmupJob failed — app still functional: {}", e.getMessage());
        }
    }
}
