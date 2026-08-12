package com.portfolio.basket.scheduler;

import com.portfolio.basket.service.BasketCatalogService;
import com.portfolio.basket.service.EnrichedEtfService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

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
            var catalog = catalogService.getCatalog();
            log.info("Cache warm-up: catalog loaded ({} ETFs)", catalog.getItems() != null ? catalog.getItems().size() : 0);

            if (catalog.getItems() != null) {
                catalog.getItems().stream()
                    .filter(item -> item.getEtfIsin() != null)
                    .limit(10)
                    .forEach(item -> {
                        try {
                            enrichedEtfService.getEnrichedEtf(item.getEtfIsin());
                            log.info("Cache warm-up: loaded ETF {}", item.getEtfIsin());
                        } catch (Exception e) {
                            log.warn("Cache warm-up failed for ETF {}: {}", item.getEtfIsin(), e.getMessage());
                        }
                    });
            }
        } catch (Exception e) {
            log.warn("BasketCacheWarmupJob failed — app still functional: {}", e.getMessage());
        }
    }
}
